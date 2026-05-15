package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lixin.probe.entity.ChangeLog;
import com.lixin.probe.entity.DataSnapshot;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.mapper.ChangeLogMapper;
import com.lixin.probe.mapper.DataSnapshotMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.service.ChangeDetectionService;
import com.lixin.probe.listener.ChangeTriggeredSyncListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class ChangeDetectionServiceImpl implements ChangeDetectionService {

    @Autowired
    private DataSnapshotMapper snapshotMapper;

    @Autowired
    private ChangeLogMapper changeLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private ProbeMapper probeMapper;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private com.lixin.probe.service.ChangeAlertService changeAlertService;

    @Override
    @Transactional
    @com.lixin.probe.annotation.DistributedLock(key = "'detect:' + #probeKey + ':' + #tableName", waitTime = 3, leaseTime = 30)
    public List<ChangeLog> saveSnapshotAndDetect(String probeKey, String databaseName,
                                                   String tableName, Long rowCount,
                                                   Long dataSize, Long indexSize,
                                                   String maxUpdateTime) {
        // 保存新快照
        DataSnapshot snapshot = DataSnapshot.builder()
                .probeKey(probeKey)
                .databaseName(databaseName)
                .tableName(tableName)
                .rowCount(rowCount)
                .dataSize(dataSize)
                .indexSize(indexSize)
                .maxUpdateTime(maxUpdateTime)
                .snapshotTime(LocalDateTime.now())
                .build();
        snapshotMapper.insert(snapshot);

        // 获取上一次快照进行对比
        List<DataSnapshot> previous = snapshotMapper.selectLatest(probeKey, tableName, 2);
        List<ChangeLog> changes = new ArrayList<>();

        if (previous.size() >= 2) {
            // previous[0] 是最新（刚插入的），previous[1] 是上一次
            DataSnapshot before = previous.get(1);
            changes.addAll(compareSnapshots(before, snapshot));
        } else if (previous.size() == 1 && !previous.get(0).getId().equals(snapshot.getId())) {
            changes.addAll(compareSnapshots(previous.get(0), snapshot));
        }

        // 批量保存变化记录
        for (ChangeLog change : changes) {
            changeLogMapper.insert(change);
            log.info("[变化探测] probe={}, table={}, type={}, rows={}",
                    probeKey, tableName, change.getChangeType(), change.getAffectedRows());
        }

        // 触发告警检查
        if (!changes.isEmpty()) {
            try {
                changeAlertService.processChangeLogs(changes);
            } catch (Exception e) {
                log.warn("[变化探测] 告警处理失败: {}", e.getMessage());
            }
        }

        return changes;
    }

    private List<ChangeLog> compareSnapshots(DataSnapshot before, DataSnapshot after) {
        List<ChangeLog> changes = new ArrayList<>();

        // 行数变化 (L1)
        if (before.getRowCount() != null && after.getRowCount() != null) {
            long diff = after.getRowCount() - before.getRowCount();
            if (diff != 0) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("before", before.getRowCount());
                detail.put("after", after.getRowCount());
                detail.put("diff", diff);

                String changeType = diff > 0 ? "ROW_INSERT" : "ROW_DELETE";
                changes.add(ChangeLog.builder()
                        .probeKey(after.getProbeKey())
                        .databaseName(after.getDatabaseName())
                        .tableName(after.getTableName())
                        .changeType(changeType)
                        .changeDetail(toJson(detail))
                        .affectedRows(Math.abs(diff))
                        .snapshotBeforeId(before.getId())
                        .snapshotAfterId(after.getId())
                        .detectedTime(LocalDateTime.now())
                        .build());
            }
        }

        // 数据大小变化
        if (before.getDataSize() != null && after.getDataSize() != null) {
            long diff = after.getDataSize() - before.getDataSize();
            if (diff != 0) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("before", before.getDataSize());
                detail.put("after", after.getDataSize());
                detail.put("diff", diff);
                detail.put("diffReadable", formatBytes(diff));

                changes.add(ChangeLog.builder()
                        .probeKey(after.getProbeKey())
                        .databaseName(after.getDatabaseName())
                        .tableName(after.getTableName())
                        .changeType("SIZE_CHANGE")
                        .changeDetail(toJson(detail))
                        .affectedRows(Math.abs(diff))
                        .snapshotBeforeId(before.getId())
                        .snapshotAfterId(after.getId())
                        .detectedTime(LocalDateTime.now())
                        .build());
            }
        }

        // 索引大小变化
        if (before.getIndexSize() != null && after.getIndexSize() != null) {
            long diff = after.getIndexSize() - before.getIndexSize();
            if (diff != 0) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("before", before.getIndexSize());
                detail.put("after", after.getIndexSize());
                detail.put("diff", diff);

                changes.add(ChangeLog.builder()
                        .probeKey(after.getProbeKey())
                        .databaseName(after.getDatabaseName())
                        .tableName(after.getTableName())
                        .changeType("INDEX_SIZE_CHANGE")
                        .changeDetail(toJson(detail))
                        .affectedRows(Math.abs(diff))
                        .snapshotBeforeId(before.getId())
                        .snapshotAfterId(after.getId())
                        .detectedTime(LocalDateTime.now())
                        .build());
            }
        }

        // Checksum 变化 (L2) — 行数相同但内容不同
        if (before.getDataChecksum() != null && after.getDataChecksum() != null
                && !before.getDataChecksum().equals(after.getDataChecksum())) {
            boolean rowUnchanged = before.getRowCount() != null && after.getRowCount() != null
                    && before.getRowCount().equals(after.getRowCount());
            if (rowUnchanged) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("beforeChecksum", before.getDataChecksum());
                detail.put("afterChecksum", after.getDataChecksum());
                detail.put("note", "Data content changed without row count change (possible UPDATE in-place)");

                changes.add(ChangeLog.builder()
                        .probeKey(after.getProbeKey())
                        .databaseName(after.getDatabaseName())
                        .tableName(after.getTableName())
                        .changeType("CHECKSUM_CHANGE")
                        .changeDetail(toJson(detail))
                        .affectedRows(1L)
                        .snapshotBeforeId(before.getId())
                        .snapshotAfterId(after.getId())
                        .detectedTime(LocalDateTime.now())
                        .build());
            }
        }

        // 更新时间变化
        if (after.getMaxUpdateTime() != null && !after.getMaxUpdateTime().isEmpty()
                && before.getMaxUpdateTime() != null && !before.getMaxUpdateTime().isEmpty()
                && !after.getMaxUpdateTime().equals(before.getMaxUpdateTime())) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before.getMaxUpdateTime());
            detail.put("after", after.getMaxUpdateTime());

            changes.add(ChangeLog.builder()
                    .probeKey(after.getProbeKey())
                    .databaseName(after.getDatabaseName())
                    .tableName(after.getTableName())
                    .changeType("DATA_UPDATE")
                    .changeDetail(toJson(detail))
                    .affectedRows(1L)
                    .snapshotBeforeId(before.getId())
                    .snapshotAfterId(after.getId())
                    .detectedTime(LocalDateTime.now())
                    .build());
        }

        return changes;
    }

    @Override
    public List<DataSnapshot> getLatestSnapshots(String probeKey, String tableName, int limit) {
        return snapshotMapper.selectLatest(probeKey, tableName, limit);
    }

    @Override
    public Page<ChangeLog> getChangeLogPage(String probeKey, String tableName,
                                              String changeType, int pageNum, int pageSize) {
        Page<ChangeLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ChangeLog> wrapper = new LambdaQueryWrapper<>();

        if (probeKey != null && !probeKey.isEmpty()) {
            wrapper.eq(ChangeLog::getProbeKey, probeKey);
        }
        if (tableName != null && !tableName.isEmpty()) {
            wrapper.eq(ChangeLog::getTableName, tableName);
        }
        if (changeType != null && !changeType.isEmpty()) {
            wrapper.eq(ChangeLog::getChangeType, changeType);
        }
        wrapper.orderByDesc(ChangeLog::getDetectedTime);

        return changeLogMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getChangeStatistics(String probeKey) {
        Map<String, Object> stats = new LinkedHashMap<>();

        String sql = "SELECT change_type, COUNT(*) as cnt FROM change_log";
        Object[] args;
        if (probeKey != null && !probeKey.isEmpty()) {
            sql += " WHERE probe_key = ? GROUP BY change_type";
            args = new Object[]{probeKey};
        } else {
            sql += " GROUP BY change_type";
            args = new Object[]{};
        }

        Map<String, Long> byType = new LinkedHashMap<>();
        long totalChanges = 0;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        for (Map<String, Object> row : rows) {
            String type = (String) row.get("change_type");
            Long cnt = ((Number) row.get("cnt")).longValue();
            byType.put(type, cnt);
            totalChanges += cnt;
        }

        stats.put("totalChanges", totalChanges);
        stats.put("byType", byType);

        // 涉及的表数量
        String tableSql = "SELECT COUNT(DISTINCT table_name) FROM change_log";
        if (probeKey != null && !probeKey.isEmpty()) {
            tableSql += " WHERE probe_key = ?";
            stats.put("affectedTables", jdbcTemplate.queryForObject(tableSql, Integer.class, probeKey));
        } else {
            stats.put("affectedTables", jdbcTemplate.queryForObject(tableSql, Integer.class));
        }

        // 最近一次变化时间
        String lastSql = "SELECT MAX(detected_time) FROM change_log";
        if (probeKey != null && !probeKey.isEmpty()) {
            lastSql += " WHERE probe_key = ?";
            Object lastTime = jdbcTemplate.queryForObject(lastSql, Object.class, probeKey);
            if (lastTime != null) {
                stats.put("lastChangeTime", lastTime);
            }
        } else {
            Object lastTime = jdbcTemplate.queryForObject(lastSql, Object.class);
            if (lastTime != null) {
                stats.put("lastChangeTime", lastTime);
            }
        }

        return stats;
    }

    @Override
    public List<ChangeLog> getRecentChanges(String probeKey, int limit) {
        LambdaQueryWrapper<ChangeLog> wrapper = new LambdaQueryWrapper<>();
        if (probeKey != null && !probeKey.isEmpty()) {
            wrapper.eq(ChangeLog::getProbeKey, probeKey);
        }
        wrapper.orderByDesc(ChangeLog::getDetectedTime);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        wrapper.last("LIMIT " + safeLimit);

        return changeLogMapper.selectList(wrapper);
    }

    private String toJson(Map<String, Object> map) {
        return JSON.toJSONString(map);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.1f GB", bytes / 1073741824.0);
    }

    @Override
    public List<ChangeLog> redetectFromLatestSnapshots(String probeKey) {
        List<ChangeLog> allChanges = new ArrayList<>();

        List<DataSnapshot> snapshots = snapshotMapper.selectList(
            new LambdaQueryWrapper<DataSnapshot>()
                .eq(DataSnapshot::getProbeKey, probeKey)
                .orderByDesc(DataSnapshot::getSnapshotTime));

        Map<String, List<DataSnapshot>> byTable = new LinkedHashMap<>();
        for (DataSnapshot s : snapshots) {
            String key = s.getDatabaseName() + "." + s.getTableName();
            byTable.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        for (Map.Entry<String, List<DataSnapshot>> entry : byTable.entrySet()) {
            List<DataSnapshot> tableSnapshots = entry.getValue();
            if (tableSnapshots.size() >= 2) {
                DataSnapshot latest = tableSnapshots.get(0);
                DataSnapshot previous = tableSnapshots.get(1);
                List<ChangeLog> changes = compareSnapshots(previous, latest);
                for (ChangeLog change : changes) {
                    changeLogMapper.insert(change);
                }
                allChanges.addAll(changes);
            }
        }

        if (!allChanges.isEmpty()) {
            try {
                changeAlertService.processChangeLogs(allChanges);
            } catch (Exception e) {
                log.warn("[变化探测] 告警处理失败: {}", e.getMessage());
            }
        }

        log.info("[变化探测] 重新检测完成: probe={}, tables={}, changes={}",
            probeKey, byTable.size(), allChanges.size());
        return allChanges;
    }

    @Override
    @Transactional
    public void processCDCEvents(String agentCode, String eventsJson) {
        try {
            JSONArray events = JSON.parseArray(eventsJson);
            if (events == null || events.isEmpty()) return;

            int count = 0;
            for (int i = 0; i < events.size(); i++) {
                JSONObject evt = events.getJSONObject(i);
                String operation = evt.getString("eventType");
                String database = evt.getString("database");
                String table = evt.getString("table");
                String position = evt.getString("position");

                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("operation", operation);
                detail.put("beforeData", evt.get("before"));
                detail.put("afterData", evt.get("after"));
                detail.put("cdcPosition", position);

                String changeType = switch (operation != null ? operation : "") {
                    case "INSERT" -> "ROW_INSERT";
                    case "UPDATE" -> "ROW_UPDATE";
                    case "DELETE" -> "ROW_DELETE";
                    default -> "CDC_EVENT";
                };

                ChangeLog changeLog = ChangeLog.builder()
                        .probeKey(agentCode)
                        .databaseName(database)
                        .tableName(table)
                        .changeType(changeType)
                        .changeDetail(toJson(detail))
                        .affectedRows(1L)
                        .detectedTime(java.time.LocalDateTime.now())
                        .build();
                changeLogMapper.insert(changeLog);
                count++;
            }

            log.info("[CDC] 处理了 {} 个CDC事件: agentCode={}", count, agentCode);

            // 发布CDC变更事件，触发实时同步
            if (eventPublisher != null && count > 0) {
                Set<String> publishedTables = new HashSet<>();
                for (int i = 0; i < events.size(); i++) {
                    JSONObject evt = events.getJSONObject(i);
                    String table = evt.getString("table");
                    String op = evt.getString("eventType");
                    String key = agentCode + ":" + table;
                    if (table != null && !publishedTables.contains(key)) {
                        publishedTables.add(key);
                        eventPublisher.publishEvent(
                                new ChangeTriggeredSyncListener.CDCChangeEvent(agentCode, table, op));
                    }
                }
            }

            // 触发告警检查
            if (count > 0) {
                List<ChangeLog> recent = getRecentChanges(agentCode, Math.min(count, 100));
                if (!recent.isEmpty()) {
                    try {
                        changeAlertService.processChangeLogs(recent);
                    } catch (Exception e) {
                        log.warn("[CDC] 告警处理失败: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[CDC] 处理CDC事件失败: {}", e.getMessage());
        }
    }

    @Override
    public void processDatasourceHeartbeat(String agentCode, String heartbeatJson) {
        try {
            JSONObject payload = JSON.parseObject(heartbeatJson);
            if (payload == null) return;

            JSONArray reports = payload.getJSONArray("reports");
            if (reports == null || reports.isEmpty()) {
                // 兼容旧格式：单条 report 直接传进来
                processSingleDatasourceReport(agentCode, payload);
                return;
            }

            for (int i = 0; i < reports.size(); i++) {
                processSingleDatasourceReport(agentCode, reports.getJSONObject(i));
            }
        } catch (Exception e) {
            log.warn("[数据源心跳] 处理心跳失败: {}", e.getMessage());
        }
    }

    private void processSingleDatasourceReport(String agentCode, JSONObject report) {
        String probeKey = report.getString("probeKey");
        String status = report.getString("status");
        Long latencyMs = report.getLong("latencyMs");

        if (probeKey == null) return;

        if (probeMapper != null) {
            LambdaQueryWrapper<Probe> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Probe::getProbeKey, probeKey);
            Probe probe = probeMapper.selectOne(wrapper);
            if (probe != null) {
                probe.setStatus(status != null ? status : "online");
                probe.setLastHeartbeat(LocalDateTime.now());
                probeMapper.updateById(probe);
            }
        }

        log.debug("[数据源心跳] agentCode={}, probeKey={}, status={}, latency={}ms",
                agentCode, probeKey, status, latencyMs);
    }
}
