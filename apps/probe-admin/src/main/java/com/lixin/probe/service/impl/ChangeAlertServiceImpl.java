package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.ChangeAlertConfig;
import com.lixin.probe.entity.ChangeAlertRecord;
import com.lixin.probe.entity.ChangeLog;
import com.lixin.probe.mapper.ChangeAlertConfigMapper;
import com.lixin.probe.mapper.ChangeAlertRecordMapper;
import com.lixin.probe.service.ChangeAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class ChangeAlertServiceImpl implements ChangeAlertService {

    @Autowired
    private ChangeAlertConfigMapper alertConfigMapper;

    @Autowired
    private ChangeAlertRecordMapper alertRecordMapper;

    @Autowired
    private com.lixin.probe.service.AlertNotificationService alertNotificationService;

    @Override
    public Page<ChangeAlertConfig> getAlertConfigs(String probeKey, int pageNum, int pageSize) {
        LambdaQueryWrapper<ChangeAlertConfig> wrapper = new LambdaQueryWrapper<ChangeAlertConfig>()
                .like(probeKey != null && !probeKey.isEmpty(), ChangeAlertConfig::getProbeKey, probeKey)
                .orderByDesc(ChangeAlertConfig::getCreateTime);
        return alertConfigMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public ChangeAlertConfig createAlertConfig(ChangeAlertConfig config) {
        config.setCreateTime(LocalDateTime.now());
        config.setEnabled(true);
        alertConfigMapper.insert(config);
        return config;
    }

    @Override
    public void updateAlertConfig(ChangeAlertConfig config) {
        alertConfigMapper.updateById(config);
    }

    @Override
    public void deleteAlertConfig(Long id) {
        alertConfigMapper.deleteById(id);
    }

    @Override
    public void processChangeLogs(List<ChangeLog> changes) {
        List<ChangeAlertConfig> configs = alertConfigMapper.selectList(
                new LambdaQueryWrapper<ChangeAlertConfig>().eq(ChangeAlertConfig::getEnabled, true));

        for (ChangeLog change : changes) {
            for (ChangeAlertConfig config : configs) {
                if (matches(change, config)) {
                    fireAlert(config, change);
                }
            }
        }
    }

    @Override
    public Page<ChangeAlertRecord> getAlertRecords(String probeKey, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<ChangeAlertRecord> wrapper = new LambdaQueryWrapper<ChangeAlertRecord>()
                .like(probeKey != null && !probeKey.isEmpty(), ChangeAlertRecord::getProbeKey, probeKey)
                .eq(status != null && !status.isEmpty(), ChangeAlertRecord::getStatus, status)
                .orderByDesc(ChangeAlertRecord::getCreatedTime);
        return alertRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Map<String, Object> getAlertStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = alertRecordMapper.selectCount(null);
        long pending = alertRecordMapper.selectCount(
                new LambdaQueryWrapper<ChangeAlertRecord>().eq(ChangeAlertRecord::getStatus, "PENDING"));
        long resolved = alertRecordMapper.selectCount(
                new LambdaQueryWrapper<ChangeAlertRecord>().eq(ChangeAlertRecord::getStatus, "RESOLVED"));

        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("resolved", resolved);
        stats.put("configCount", alertConfigMapper.selectCount(
                new LambdaQueryWrapper<ChangeAlertConfig>().eq(ChangeAlertConfig::getEnabled, true)));
        return stats;
    }

    private boolean matches(ChangeLog change, ChangeAlertConfig config) {
        if (config.getProbeKey() != null && !config.getProbeKey().isEmpty()
                && !config.getProbeKey().equals(change.getProbeKey())) {
            return false;
        }
        if (config.getTableName() != null && !config.getTableName().isEmpty()
                && !config.getTableName().equals(change.getTableName())) {
            return false;
        }
        if (config.getChangeTypes() != null && !config.getChangeTypes().isEmpty()) {
            String[] types = config.getChangeTypes().split(",");
            boolean found = false;
            for (String type : types) {
                if (type.trim().equals(change.getChangeType())) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        if (config.getThresholdRows() != null && change.getAffectedRows() != null
                && change.getAffectedRows() < config.getThresholdRows()) {
            return false;
        }
        return true;
    }

    private void fireAlert(ChangeAlertConfig config, ChangeLog change) {
        ChangeAlertRecord record = ChangeAlertRecord.builder()
                .alertConfigId(config.getId())
                .probeKey(change.getProbeKey())
                .tableName(change.getTableName())
                .changeType(change.getChangeType())
                .changeDetail(change.getChangeDetail())
                .affectedRows(change.getAffectedRows())
                .alertLevel(config.getAlertLevel())
                .notifyChannel(config.getNotifyChannels())
                .status("PENDING")
                .createdTime(LocalDateTime.now())
                .build();
        alertRecordMapper.insert(record);
        log.info("[变化告警] {} - {}: {} 变化 {} 行, 级别: {}",
                change.getProbeKey(), change.getTableName(),
                change.getChangeType(), change.getAffectedRows(), config.getAlertLevel());
        try {
            alertNotificationService.notify(record);
        } catch (Exception e) {
            log.warn("[变化告警] 通知推送失败: {}", e.getMessage());
        }
    }
}
