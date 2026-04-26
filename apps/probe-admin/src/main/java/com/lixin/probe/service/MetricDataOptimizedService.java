package com.lixin.probe.service;

import com.lixin.probe.entity.MetricData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 监控数据服务接口（优化版）
 * 提供批量查询支持，避免N+1查询问题
 *
 * @author Claude Code
 * @date 2026-04-12
 */
public interface MetricDataOptimizedService {

    /**
     * 批量获取探针指标数据
     *
     * @param probeIds 探针ID列表
     * @return 探针ID -> 指标数据列表的映射
     */
    Map<Long, List<MetricData>> getProbeMetricsBatch(List<Long> probeIds);

    /**
     * 批量获取探针详情（含指标）
     *
     * @param probeIds 探针ID列表
     * @return 探针详情列表
     */
    List<?> getProbeDetailsBatch(List<Long> probeIds);

    /**
     * 获取探针指标摘要
     *
     * @param probeId 探针ID
     * @return 指标摘要
     */
    Object getProbeMetricsSummary(Long probeId);

    /**
     * 清除探针缓存
     *
     * @param probeId 探针ID
     */
    void evictProbeCache(Long probeId);

    /**
     * 批量保存指标数据
     *
     * @param metrics 指标数据列表
     * @return 保存数量
     */
    int batchSave(List<MetricData> metrics);
}
