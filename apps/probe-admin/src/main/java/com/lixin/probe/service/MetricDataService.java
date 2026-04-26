package com.lixin.probe.service;

import com.lixin.probe.dto.ProbeMetricsSummary;
import com.lixin.probe.entity.MetricData;

import java.util.List;

/**
 * 监控数据Service
 */
public interface MetricDataService {

    /**
     * 查询探针指标数据
     */
    List<MetricData> getProbeMetrics(Long probeId, String metricName, String startTime, String endTime);

    /**
     * 获取探针最新指标数据
     */
    List<MetricData> getLatestMetrics(Long probeId);

    /**
     * 保存监控数据
     */
    void save(MetricData metricData);

    /**
     * 批量保存监控数据
     */
    void batchSave(List<MetricData> metrics);

    /**
     * 获取探针指标摘要（用于前端显示）
     *
     * @param probeId 探针ID
     * @return 指标摘要，包含CPU使用率、内存使用率等核心指标
     */
    ProbeMetricsSummary getMetricsSummary(Long probeId);
}
