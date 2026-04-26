package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.DataSourceAlertConfig;
import com.lixin.probe.entity.DataSourceAlertRecord;
import com.lixin.probe.entity.ChangeAlertRecord;
import com.lixin.probe.mapper.DataSourceAlertConfigMapper;
import com.lixin.probe.mapper.DataSourceAlertRecordMapper;
import com.lixin.probe.service.AlertNotificationService;
import com.lixin.probe.service.DataSourceAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DataSourceAlertServiceImpl implements DataSourceAlertService {

    @Autowired
    private DataSourceAlertConfigMapper alertConfigMapper;

    @Autowired
    private DataSourceAlertRecordMapper alertRecordMapper;

    @Autowired(required = false)
    private AlertNotificationService alertNotificationService;

    private final ConcurrentHashMap<String, Integer> failureCounter = new ConcurrentHashMap<>();

    @Override
    public Page<DataSourceAlertConfig> getAlertConfigs(String probeKey, int pageNum, int pageSize) {
        LambdaQueryWrapper<DataSourceAlertConfig> wrapper = new LambdaQueryWrapper<DataSourceAlertConfig>()
                .like(probeKey != null && !probeKey.isEmpty(), DataSourceAlertConfig::getProbeKey, probeKey)
                .orderByDesc(DataSourceAlertConfig::getCreateTime);
        return alertConfigMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public DataSourceAlertConfig createAlertConfig(DataSourceAlertConfig config) {
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        config.setEnabled(true);
        alertConfigMapper.insert(config);
        return config;
    }

    @Override
    public void updateAlertConfig(DataSourceAlertConfig config) {
        config.setUpdateTime(LocalDateTime.now());
        alertConfigMapper.updateById(config);
    }

    @Override
    public void deleteAlertConfig(Long id) {
        alertConfigMapper.deleteById(id);
    }

    @Override
    public void processHeartbeat(String agentCode, List<Map<String, Object>> heartbeatData) {
        if (heartbeatData == null || heartbeatData.isEmpty()) return;

        List<DataSourceAlertConfig> configs = alertConfigMapper.selectList(
                new LambdaQueryWrapper<DataSourceAlertConfig>().eq(DataSourceAlertConfig::getEnabled, true));

        for (Map<String, Object> ds : heartbeatData) {
            String probeKey = (String) ds.get("probeKey");
            String dsName = (String) ds.getOrDefault("name", probeKey);
            String status = (String) ds.getOrDefault("status", "online");
            Long latencyMs = ds.get("latencyMs") != null ? ((Number) ds.get("latencyMs")).longValue() : null;
            String errorMsg = (String) ds.get("errorMessage");

            String key = agentCode + ":" + probeKey;

            if ("offline".equals(status) || "error".equals(status)) {
                int count = failureCounter.merge(key, 1, Integer::sum);
                for (DataSourceAlertConfig config : configs) {
                    if (matchesConfig(probeKey, config) && count >= config.getConsecutiveFailures()) {
                        if (!hasRecentAlert(probeKey, dsName, config.getId())) {
                            fireAlert(config, probeKey, dsName, status, latencyMs, errorMsg, count);
                        }
                    }
                }
            } else {
                Integer prev = failureCounter.remove(key);
                if (prev != null && prev > 0) {
                    resolvePendingAlerts(probeKey, dsName);
                    log.info("[数据源告警] 数据源恢复: {} ({})", dsName, probeKey);
                }
            }
        }
    }

    @Override
    public Page<DataSourceAlertRecord> getAlertRecords(String probeKey, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<DataSourceAlertRecord> wrapper = new LambdaQueryWrapper<DataSourceAlertRecord>()
                .like(probeKey != null && !probeKey.isEmpty(), DataSourceAlertRecord::getProbeKey, probeKey)
                .eq(status != null && !status.isEmpty(), DataSourceAlertRecord::getAlertStatus, status)
                .orderByDesc(DataSourceAlertRecord::getCreatedTime);
        return alertRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Map<String, Object> getAlertStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = alertRecordMapper.selectCount(null);
        long pending = alertRecordMapper.selectCount(
                new LambdaQueryWrapper<DataSourceAlertRecord>().eq(DataSourceAlertRecord::getAlertStatus, "PENDING"));
        long resolved = alertRecordMapper.selectCount(
                new LambdaQueryWrapper<DataSourceAlertRecord>().eq(DataSourceAlertRecord::getAlertStatus, "RESOLVED"));
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("resolved", resolved);
        stats.put("configCount", alertConfigMapper.selectCount(
                new LambdaQueryWrapper<DataSourceAlertConfig>().eq(DataSourceAlertConfig::getEnabled, true)));
        return stats;
    }

    private boolean matchesConfig(String probeKey, DataSourceAlertConfig config) {
        if (config.getProbeKey() == null || config.getProbeKey().isEmpty()) return true;
        return probeKey != null && probeKey.contains(config.getProbeKey());
    }

    private boolean hasRecentAlert(String probeKey, String dsName, Long configId) {
        return alertRecordMapper.selectCount(
                new LambdaQueryWrapper<DataSourceAlertRecord>()
                        .eq(DataSourceAlertRecord::getProbeKey, probeKey)
                        .eq(DataSourceAlertRecord::getDatasourceName, dsName)
                        .eq(DataSourceAlertRecord::getAlertStatus, "PENDING")) > 0;
    }

    private void fireAlert(DataSourceAlertConfig config, String probeKey, String dsName,
                           String status, Long latencyMs, String errorMsg, int count) {
        DataSourceAlertRecord record = DataSourceAlertRecord.builder()
                .alertConfigId(config.getId())
                .probeKey(probeKey)
                .datasourceName(dsName)
                .status(status)
                .latencyMs(latencyMs)
                .errorMessage(errorMsg)
                .consecutiveCount(count)
                .alertLevel(config.getAlertLevel())
                .notifyChannel(config.getNotifyChannels())
                .alertStatus("PENDING")
                .createdTime(LocalDateTime.now())
                .build();
        alertRecordMapper.insert(record);
        log.warn("[数据源告警] {} - {}: 连续失败 {} 次, 级别: {}", probeKey, dsName, count, config.getAlertLevel());

        if (alertNotificationService != null) {
            try {
                ChangeAlertRecord changeAlert = ChangeAlertRecord.builder()
                        .probeKey(probeKey)
                        .tableName(dsName)
                        .changeType("DATASOURCE_" + status)
                        .changeDetail(errorMsg)
                        .alertLevel(config.getAlertLevel())
                        .notifyChannel(config.getNotifyChannels())
                        .status("PENDING")
                        .createdTime(LocalDateTime.now())
                        .build();
                alertNotificationService.notify(changeAlert);
            } catch (Exception e) {
                log.warn("[数据源告警] 通知推送失败: {}", e.getMessage());
            }
        }
    }

    private void resolvePendingAlerts(String probeKey, String dsName) {
        List<DataSourceAlertRecord> pending = alertRecordMapper.selectList(
                new LambdaQueryWrapper<DataSourceAlertRecord>()
                        .eq(DataSourceAlertRecord::getProbeKey, probeKey)
                        .eq(DataSourceAlertRecord::getDatasourceName, dsName)
                        .eq(DataSourceAlertRecord::getAlertStatus, "PENDING"));
        for (DataSourceAlertRecord r : pending) {
            r.setAlertStatus("RESOLVED");
            r.setResolvedTime(LocalDateTime.now());
            alertRecordMapper.updateById(r);
        }
    }
}
