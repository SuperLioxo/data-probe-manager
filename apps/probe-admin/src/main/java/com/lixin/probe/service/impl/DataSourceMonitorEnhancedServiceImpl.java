package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.DatasourceMonitorLog;
import com.lixin.probe.mapper.DatasourceMonitorLogMapper;
import com.lixin.probe.service.DataSourceMonitorEnhancedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class DataSourceMonitorEnhancedServiceImpl implements DataSourceMonitorEnhancedService {

    @Autowired
    private DatasourceMonitorLogMapper monitorLogMapper;

    @Override
    public void recordMetric(String probeKey, String metricType, double value, String unit, String extraInfo) {
        DatasourceMonitorLog log = DatasourceMonitorLog.builder()
                .probeKey(probeKey)
                .metricType(metricType)
                .metricValue(value)
                .metricUnit(unit)
                .extraInfo(extraInfo)
                .collectedTime(LocalDateTime.now())
                .build();
        monitorLogMapper.insert(log);
    }

    @Override
    public Map<String, Object> getMetricsOverview(String probeKey) {
        Map<String, Object> overview = new LinkedHashMap<>();

        LambdaQueryWrapper<DatasourceMonitorLog> wrapper = new LambdaQueryWrapper<DatasourceMonitorLog>()
                .eq(probeKey != null && !probeKey.isEmpty(), DatasourceMonitorLog::getProbeKey, probeKey)
                .orderByDesc(DatasourceMonitorLog::getCollectedTime)
                .last("LIMIT 100");

        List<DatasourceMonitorLog> recentLogs = monitorLogMapper.selectList(wrapper);
        Map<String, List<DatasourceMonitorLog>> byType = new LinkedHashMap<>();
        for (DatasourceMonitorLog l : recentLogs) {
            byType.computeIfAbsent(l.getMetricType(), k -> new ArrayList<>()).add(l);
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, List<DatasourceMonitorLog>> entry : byType.entrySet()) {
            List<DatasourceMonitorLog> logs = entry.getValue();
            if (!logs.isEmpty()) {
                double latest = logs.get(0).getMetricValue();
                double avg = logs.stream().mapToDouble(DatasourceMonitorLog::getMetricValue).average().orElse(0);
                metrics.put(entry.getKey(), Map.of(
                        "latest", latest,
                        "average", Math.round(avg * 100.0) / 100.0,
                        "unit", logs.get(0).getMetricUnit() != null ? logs.get(0).getMetricUnit() : "",
                        "samples", logs.size()
                ));
            }
        }

        overview.put("probeKey", probeKey);
        overview.put("metrics", metrics);
        overview.put("totalSamples", recentLogs.size());
        return overview;
    }

    @Override
    public Page<DatasourceMonitorLog> getMetricHistory(String probeKey, String metricType, int pageNum, int pageSize) {
        LambdaQueryWrapper<DatasourceMonitorLog> wrapper = new LambdaQueryWrapper<DatasourceMonitorLog>()
                .eq(probeKey != null && !probeKey.isEmpty(), DatasourceMonitorLog::getProbeKey, probeKey)
                .eq(metricType != null && !metricType.isEmpty(), DatasourceMonitorLog::getMetricType, metricType)
                .orderByDesc(DatasourceMonitorLog::getCollectedTime);
        return monitorLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
