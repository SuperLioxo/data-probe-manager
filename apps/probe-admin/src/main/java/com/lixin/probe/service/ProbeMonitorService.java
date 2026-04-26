package com.lixin.probe.service;

/**
 * 探针监控服务接口
 * 负责探针状态监控、心跳管理等
 *
 * @author Claude Code
 * @date 2026-03-11
 */
public interface ProbeMonitorService {

    /**
     * 更新探针心跳
     * @param probeKey 探针标识
     */
    void updateHeartbeat(String probeKey);
}
