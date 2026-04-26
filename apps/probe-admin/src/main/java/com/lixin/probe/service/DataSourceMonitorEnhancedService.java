package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.DatasourceMonitorLog;

import java.util.Map;

public interface DataSourceMonitorEnhancedService {

    void recordMetric(String probeKey, String metricType, double value, String unit, String extraInfo);

    Map<String, Object> getMetricsOverview(String probeKey);

    Page<DatasourceMonitorLog> getMetricHistory(String probeKey, String metricType, int pageNum, int pageSize);
}
