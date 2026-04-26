package com.lixin.probe.service;

import java.util.Map;

/**
 * 统计Service
 */
public interface StatisticsService {

    /**
     * 获取探针统计
     */
    Map<String, Object> getProbeStatistics();

    /**
     * 获取告警统计
     */
    Map<String, Object> getAlertStatistics();

    /**
     * 获取概览统计
     * @param timeRange 时间范围 (1h, 6h, 24h, 7d, 30d)
     */
    Map<String, Object> getOverview(String timeRange);

    /**
     * 获取探针趋势数据
     */
    Map<String, Object> getProbeTrend(String timeRange, String metric);

    /**
     * 获取指标分布统计
     */
    Map<String, Object> getMetricDistribution(String timeRange);

    /**
     * 获取告警趋势统计
     */
    Map<String, Object> getAlertTrend(String timeRange);

    /**
     * 获取探针状态趋势统计
     */
    Map<String, Object> getProbeStatusTrend(String timeRange);

    /**
     * 获取探针排行统计
     */
    Map<String, Object> getProbeRanking(String timeRange, String metric);
}
