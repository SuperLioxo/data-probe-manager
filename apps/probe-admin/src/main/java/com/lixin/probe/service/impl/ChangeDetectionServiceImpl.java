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

/**
 * 数据变化检测服务实现类。
 * <p>
 * 通过快照对比机制检测表级别的数据变更，支持行数、数据大小、索引大小、
 * 数据校验和（Checksum）、更新时间等多维度的变化检测。
 * 同时支持 CDC（Change Data Capture）实时事件处理，能够接收并处理来自
 * Agent 的 INSERT、UPDATE、DELETE 操作事件，实现准实时的数据变更感知。
 * </p>
 *
 * <p>核心能力：</p>
 * <ul>
 *   <li>快照保存与对比：每次采集时保存快照，与上一次快照进行逐字段对比</li>
 *   <li>CDC 事件处理：接收 Agent 推送的 CDC 事件，直接生成变更记录</li>
 *   <li>数据源心跳：处理 Agent 心跳上报，维护探针在线状态</li>
 *   <li>变化统计：基于 SQL 聚合查询提供变更统计信息</li>
 * </ul>
 */
@Slf4j
@Service
public class ChangeDetectionServiceImpl implements ChangeDetectionService {

    /** 数据快照持久层，用于保存和查询历史快照 */
    @Autowired
    private DataSnapshotMapper snapshotMapper;

    /** 变更日志持久层，用于记录每次检测到的数据变化 */
    @Autowired
    private ChangeLogMapper changeLogMapper;

    /** JDBC 模板，用于执行原生 SQL 聚合统计查询 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 探针持久层（可选注入），用于心跳处理时更新探针状态 */
    @Autowired(required = false)
    private ProbeMapper probeMapper;

    /** Spring 事件发布器（可选注入），用于发布 CDC 变更事件以触发实时同步 */
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    /** 变更告警服务，用于在检测到变化后触发告警规则评估 */
    @Autowired
    private com.lixin.probe.service.ChangeAlertService changeAlertService;

    /**
     * 保存新快照并与前一次快照对比，检测数据变化。
     * <p>
     * 该方法是快照对比检测的核心入口，执行流程如下：
     * <ol>
     *   <li>构建并持久化当前时刻的数据快照</li>
     *   <li>查询该表最近两次快照（包含刚插入的），取前一次作为对比基准</li>
     *   <li>调用 {@link #compareSnapshots} 进行逐字段对比，生成变更记录</li>
     *   <li>批量保存变更记录到 change_log 表</li>
     *   <li>触发告警规则检查</li>
     * </ol>
     * </p>
     *
     * <p>使用分布式锁保证同一探针同一表不会并发执行检测，避免快照对比错乱。</p>
     *
     * @param probeKey      探针唯一标识
     * @param databaseName  数据库名称
     * @param tableName     表名称
     * @param rowCount      当前行数
     * @param dataSize      数据大小（字节）
     * @param indexSize     索引大小（字节）
     * @param maxUpdateTime 最大更新时间
     * @return 检测到的变更记录列表，无变化时返回空列表
     */
    @Override
    @Transactional
    @com.lixin.probe.annotation.DistributedLock(key = "'detect:' + #probeKey + ':' + #tableName", waitTime = 3, leaseTime = 30)
    public List<ChangeLog> saveSnapshotAndDetect(String probeKey, String databaseName,
                                                   String tableName, Long rowCount,
                                                   Long dataSize, Long indexSize,
                                                   String maxUpdateTime) {
        // 构建当前时刻的数据快照对象
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

        // 查询该表最近的两条快照记录（按时间倒序），用于定位前一次快照作为对比基准
        List<DataSnapshot> previous = snapshotMapper.selectLatest(probeKey, tableName, 2);
        List<ChangeLog> changes = new ArrayList<>();

        if (previous.size() >= 2) {
            // 列表长度 >= 2 说明存在历史快照：previous[0] 是刚插入的最新快照，previous[1] 是上一次快照
            DataSnapshot before = previous.get(1);
            changes.addAll(compareSnapshots(before, snapshot));
        } else if (previous.size() == 1 && !previous.get(0).getId().equals(snapshot.getId())) {
            // 仅有一条且不是刚插入的那条，说明这是首次有历史记录可供对比
            changes.addAll(compareSnapshots(previous.get(0), snapshot));
        }

        // 逐条保存检测到的变更记录
        for (ChangeLog change : changes) {
            changeLogMapper.insert(change);
            log.info("[变化探测] probe={}, table={}, type={}, rows={}",
                    probeKey, tableName, change.getChangeType(), change.getAffectedRows());
        }

        // 若存在变更，触发告警规则检查（告警失败不影响主流程）
        if (!changes.isEmpty()) {
            try {
                changeAlertService.processChangeLogs(changes);
            } catch (Exception e) {
                log.warn("[变化探测] 告警处理失败: {}", e.getMessage());
            }
        }

        return changes;
    }

    /**
     * 逐字段对比两个快照，生成变更记录列表。
     * <p>
     * 对比维度包括：
     * <ul>
     *   <li><b>行数变化（L1）</b>：行数增加标记为 ROW_INSERT，减少标记为 ROW_DELETE</li>
     *   <li><b>数据大小变化</b>：记录变更前后的字节数及人类可读的差值</li>
     *   <li><b>索引大小变化</b>：记录索引空间的增减</li>
     *   <li><b>Checksum 变化（L2）</b>：行数不变但数据内容发生变化（通常为 UPDATE 就地更新）</li>
     *   <li><b>更新时间变化</b>：最大更新时间的偏移</li>
     * </ul>
     * 每种维度独立检测，一次对比可能产生多条变更记录。
     * </p>
     *
     * @param before 对比基准快照（较早的快照）
     * @param after  当前快照（较新的快照）
     * @return 检测到的所有变更记录列表
     */
    private List<ChangeLog> compareSnapshots(DataSnapshot before, DataSnapshot after) {
        List<ChangeLog> changes = new ArrayList<>();

        // ---- 行数变化检测（L1 级别：行数增减） ----
        if (before.getRowCount() != null && after.getRowCount() != null) {
            long diff = after.getRowCount() - before.getRowCount();
            if (diff != 0) {
                // 行数差异不为零，记录变更详情
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("before", before.getRowCount());
                detail.put("after", after.getRowCount());
                detail.put("diff", diff);

                // 差值为正表示新增行，为负表示删除行
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

        // ---- 数据大小变化检测 ----
        if (before.getDataSize() != null && after.getDataSize() != null) {
            long diff = after.getDataSize() - before.getDataSize();
            if (diff != 0) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("before", before.getDataSize());
                detail.put("after", after.getDataSize());
                detail.put("diff", diff);
                // 附带人类可读的格式化大小（如 1.5 MB），便于在告警通知中展示
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

        // ---- 索引大小变化检测 ----
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

        // ---- Checksum 变化检测（L2 级别：行数不变但数据内容发生变化） ----
        // 当行数相同但校验和不同时，说明发生了 UPDATE 原地更新操作
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

        // ---- 更新时间变化检测 ----
        // 最大更新时间发生偏移说明表内数据被修改
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

    /**
     * 获取指定探针和表的最近 N 条快照记录。
     *
     * @param probeKey  探针唯一标识
     * @param tableName 表名称
     * @param limit     查询数量限制
     * @return 快照列表，按时间倒序排列
     */
    @Override
    public List<DataSnapshot> getLatestSnapshots(String probeKey, String tableName, int limit) {
        return snapshotMapper.selectLatest(probeKey, tableName, limit);
    }

    /**
     * 分页查询变更日志，支持按探针、表名、变更类型进行筛选。
     *
     * @param probeKey   探针唯一标识（可选过滤条件）
     * @param tableName  表名称（可选过滤条件）
     * @param changeType 变更类型（可选过滤条件），如 ROW_INSERT、ROW_DELETE、SIZE_CHANGE 等
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页记录数
     * @return 分页变更日志结果
     */
    @Override
    public Page<ChangeLog> getChangeLogPage(String probeKey, String tableName,
                                              String changeType, int pageNum, int pageSize) {
        Page<ChangeLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ChangeLog> wrapper = new LambdaQueryWrapper<>();

        // 按可选条件动态构建查询过滤
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

    /**
     * 获取变更统计信息，使用 SQL 聚合查询实现高性能统计。
     * <p>
     * 统计内容包括：
     * <ul>
     *   <li>totalChanges：总变更次数</li>
     *   <li>byType：按变更类型分组的计数（如 ROW_INSERT、SIZE_CHANGE 等）</li>
     *   <li>affectedTables：涉及的表数量（去重统计）</li>
     *   <li>lastChangeTime：最近一次变更的时间</li>
     * </ul>
     * 注意：本方法使用原生 SQL 聚合而非 Java 内存迭代，适合大数据量场景。
     * </p>
     *
     * @param probeKey 探针唯一标识（为空时统计全局数据）
     * @return 统计结果 Map，包含 totalChanges、byType、affectedTables、lastChangeTime 等键
     */
    @Override
    public Map<String, Object> getChangeStatistics(String probeKey) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 使用 SQL GROUP BY 聚合按变更类型统计数量，避免将全量数据加载到内存
        String sql = "SELECT change_type, COUNT(*) as cnt FROM change_log";
        Object[] args;
        if (probeKey != null && !probeKey.isEmpty()) {
            sql += " WHERE probe_key = ? GROUP BY change_type";
            args = new Object[]{probeKey};
        } else {
            sql += " GROUP BY change_type";
            args = new Object[]{};
        }

        // 遍历聚合结果，汇总各类型计数和总变更数
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

        // 统计涉及的表数量（DISTINCT 去重）
        String tableSql = "SELECT COUNT(DISTINCT table_name) FROM change_log";
        if (probeKey != null && !probeKey.isEmpty()) {
            tableSql += " WHERE probe_key = ?";
            stats.put("affectedTables", jdbcTemplate.queryForObject(tableSql, Integer.class, probeKey));
        } else {
            stats.put("affectedTables", jdbcTemplate.queryForObject(tableSql, Integer.class));
        }

        // 查询最近一次变更的发生时间
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

    /**
     * 获取指定探针最近的变更记录。
     * <p>
     * 使用安全限制机制，将查询数量钳制在 [1, 500] 区间内，防止客户端
     * 传入超大 limit 值导致查询性能问题。
     * </p>
     *
     * @param probeKey 探针唯一标识（为空时查询所有探针）
     * @param limit    期望返回的记录数量，实际会被限制在 1-500 之间
     * @return 最近的变更记录列表，按检测时间倒序排列
     */
    @Override
    public List<ChangeLog> getRecentChanges(String probeKey, int limit) {
        LambdaQueryWrapper<ChangeLog> wrapper = new LambdaQueryWrapper<>();
        if (probeKey != null && !probeKey.isEmpty()) {
            wrapper.eq(ChangeLog::getProbeKey, probeKey);
        }
        wrapper.orderByDesc(ChangeLog::getDetectedTime);
        // 安全限制：将 limit 钳制在 [1, 500] 区间，防止恶意或误传的大值查询
        int safeLimit = Math.max(1, Math.min(limit, 500));
        wrapper.last("LIMIT " + safeLimit);

        return changeLogMapper.selectList(wrapper);
    }

    /**
     * 将 Map 序列化为 JSON 字符串，用于存储变更详情。
     *
     * @param map 待序列化的键值对
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> map) {
        return JSON.toJSONString(map);
    }

    /**
     * 将字节数格式化为人类可读的大小字符串。
     * <p>
     * 自动选择合适的单位（B / KB / MB / GB），保留一位小数。
     * </p>
     *
     * @param bytes 字节数（可以为负数，表示减少）
     * @return 格式化后的大小字符串，如 "1.5 MB"
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.1f GB", bytes / 1073741824.0);
    }

    /**
     * 基于已保存的最新快照重新执行变化检测。
     * <p>
     * 该方法用于手动触发重新检测场景（如告警规则变更后需要重新评估历史数据）。
     * 执行流程：
     * <ol>
     *   <li>查询该探针下所有历史快照，按表名分组</li>
     *   <li>对每张表取最新两条快照进行对比</li>
     *   <li>将新检测到的变更记录保存并触发告警检查</li>
     * </ol>
     * </p>
     *
     * @param probeKey 探针唯一标识
     * @return 重新检测到的所有变更记录列表
     */
    @Override
    public List<ChangeLog> redetectFromLatestSnapshots(String probeKey) {
        List<ChangeLog> allChanges = new ArrayList<>();

        // 查询该探针下的全部快照，按时间倒序排列
        List<DataSnapshot> snapshots = snapshotMapper.selectList(
            new LambdaQueryWrapper<DataSnapshot>()
                .eq(DataSnapshot::getProbeKey, probeKey)
                .orderByDesc(DataSnapshot::getSnapshotTime));

        // 按 "数据库.表名" 分组，以便对每张表独立进行快照对比
        Map<String, List<DataSnapshot>> byTable = new LinkedHashMap<>();
        for (DataSnapshot s : snapshots) {
            String key = s.getDatabaseName() + "." + s.getTableName();
            byTable.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        // 对每张表取最新两条快照进行对比检测
        for (Map.Entry<String, List<DataSnapshot>> entry : byTable.entrySet()) {
            List<DataSnapshot> tableSnapshots = entry.getValue();
            if (tableSnapshots.size() >= 2) {
                // 由于已按时间倒序排列，第一条是最新快照，第二条是上一次快照
                DataSnapshot latest = tableSnapshots.get(0);
                DataSnapshot previous = tableSnapshots.get(1);
                List<ChangeLog> changes = compareSnapshots(previous, latest);
                for (ChangeLog change : changes) {
                    changeLogMapper.insert(change);
                }
                allChanges.addAll(changes);
            }
        }

        // 触发告警规则检查（告警失败不影响主流程）
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

    /**
     * 处理来自 Agent 的 CDC（Change Data Capture）实时事件。
     * <p>
     * CDC 事件由 Agent 端的 Binlog/ wal 监听组件产生，包含 INSERT、UPDATE、DELETE
     * 等数据操作事件。本方法负责：
     * <ol>
     *   <li>解析事件 JSON 数组，提取每个事件的操作类型、数据库、表名、变更前后数据</li>
     *   <li>将 CDC 事件转换为标准的变更日志（ChangeLog）记录并持久化</li>
     *   <li>发布 Spring 应用事件（CDCChangeEvent），触发下游的实时同步流程</li>
     *   <li>触发告警规则检查，对异常变更进行告警通知</li>
     * </ol>
     * 注意：同一批次中相同探针+表名的事件只会发布一次同步事件，避免重复处理。
     * </p>
     *
     * @param agentCode  Agent 标识编码，作为探针键使用
     * @param eventsJson CDC 事件 JSON 数组字符串，格式示例：
     *                   [{"eventType":"INSERT","database":"db1","table":"t1","before":null,"after":{...},"position":"binlog-123"}]
     */
    @Override
    @Transactional
    public void processCDCEvents(String agentCode, String eventsJson) {
        try {
            // 解析 CDC 事件数组
            JSONArray events = JSON.parseArray(eventsJson);
            if (events == null || events.isEmpty()) return;

            int count = 0;
            for (int i = 0; i < events.size(); i++) {
                JSONObject evt = events.getJSONObject(i);
                // 提取事件核心字段：操作类型、目标库表、CDC 位点
                String operation = evt.getString("eventType");
                String database = evt.getString("database");
                String table = evt.getString("table");
                String position = evt.getString("position");

                // 构建变更详情，包含操作类型、变更前后数据和 CDC 位点信息
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("operation", operation);
                detail.put("beforeData", evt.get("before"));
                detail.put("afterData", evt.get("after"));
                detail.put("cdcPosition", position);

                // 将 CDC 操作类型映射为内部变更类型
                String changeType = switch (operation != null ? operation : "") {
                    case "INSERT" -> "ROW_INSERT";
                    case "UPDATE" -> "ROW_UPDATE";
                    case "DELETE" -> "ROW_DELETE";
                    default -> "CDC_EVENT";
                };

                // 构建并持久化变更日志记录
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

            // 发布 CDC 变更事件，触发实时同步（如增量数据拉取）
            // 同一批次中相同的 agentCode+table 组合仅发布一次事件，避免重复同步
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

            // CDC 事件处理完成后，触发告警规则检查
            if (count > 0) {
                // 取最近的变更记录送入告警评估，数量限制为本批次事件数（最多 100 条）
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

    /**
     * 处理数据源心跳上报，更新探针在线状态。
     * <p>
     * 支持两种心跳格式：
     * <ul>
     *   <li>批量格式：JSON 中包含 "reports" 数组，每个元素为一条探针状态报告</li>
     *   <li>单条格式：JSON 直接为一条探针状态报告（兼容旧版 Agent）</li>
     * </ul>
     * 心跳处理会更新探针的状态（online/offline 等）和最后心跳时间，
     * 用于探针在线状态监控和离线告警。
     * </p>
     *
     * @param agentCode     Agent 标识编码
     * @param heartbeatJson 心跳 JSON 字符串
     */
    @Override
    public void processDatasourceHeartbeat(String agentCode, String heartbeatJson) {
        try {
            JSONObject payload = JSON.parseObject(heartbeatJson);
            if (payload == null) return;

            // 检查是否为批量报告格式（包含 reports 数组）
            JSONArray reports = payload.getJSONArray("reports");
            if (reports == null || reports.isEmpty()) {
                // 兼容旧格式：单条 report 直接传进来
                processSingleDatasourceReport(agentCode, payload);
                return;
            }

            // 逐条处理批量报告中的每条探针状态
            for (int i = 0; i < reports.size(); i++) {
                processSingleDatasourceReport(agentCode, reports.getJSONObject(i));
            }
        } catch (Exception e) {
            log.warn("[数据源心跳] 处理心跳失败: {}", e.getMessage());
        }
    }

    /**
     * 处理单条数据源探针状态报告。
     * <p>
     * 根据报告中的探针键查找对应的探针记录，更新其状态和最后心跳时间。
     * 若探针记录不存在则跳过。状态字段为空时默认设为 "online"。
     * </p>
     *
     * @param agentCode Agent 标识编码
     * @param report    单条探针状态报告 JSON 对象，包含 probeKey、status、latencyMs 字段
     */
    private void processSingleDatasourceReport(String agentCode, JSONObject report) {
        String probeKey = report.getString("probeKey");
        String status = report.getString("status");
        Long latencyMs = report.getLong("latencyMs");

        if (probeKey == null) return;

        // 查找探针记录并更新在线状态与心跳时间
        if (probeMapper != null) {
            LambdaQueryWrapper<Probe> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Probe::getProbeKey, probeKey);
            Probe probe = probeMapper.selectOne(wrapper);
            if (probe != null) {
                // 状态为空时默认标记为 online，表示探针正常在线
                probe.setStatus(status != null ? status : "online");
                probe.setLastHeartbeat(LocalDateTime.now());
                probeMapper.updateById(probe);
            }
        }

        log.debug("[数据源心跳] agentCode={}, probeKey={}, status={}, latency={}ms",
                agentCode, probeKey, status, latencyMs);
    }
}
