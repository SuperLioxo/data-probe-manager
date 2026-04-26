package com.lixin.probe.service;

/**
 * 调度引擎Service
 */
public interface ScheduleEngine {

    /**
     * 检查探针健康状态
     */
    void healthCheck();

    /**
     * 检测离线探针
     */
    void detectOfflineProbes();

    /**
     * 清理历史数据
     */
    void cleanHistoryData();
}
