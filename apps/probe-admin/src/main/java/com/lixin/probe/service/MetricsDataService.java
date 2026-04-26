package com.lixin.probe.service;

import com.lixin.probe.dto.MetricsData;
import com.lixin.probe.entity.Probe;

/**
 * WebSocket实时指标推送服务接口
 *
 * @author Claude Code
 * @date 2026-04-13
 */
public interface MetricsDataService {

    /**
     * 获取探针的实时指标数据
     *
     * @param probeId 探针ID
     * @return 指标数据
     */
    MetricsData getMetricsData(Long probeId);

    /**
     * 获取所有在线探针的实时指标数据
     *
     * @return 指标数据列表
     */
    java.util.List<MetricsData> getAllOnlineMetricsData();
}
