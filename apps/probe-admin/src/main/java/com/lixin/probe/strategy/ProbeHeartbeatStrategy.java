package com.lixin.probe.strategy;

import com.lixin.probe.entity.Probe;

/**
 * 探针心跳策略接口
 * 定义不同类型探针的心跳处理逻辑
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public interface ProbeHeartbeatStrategy {

    /**
     * 处理心跳
     *
     * @param probe 探针对象
     * @return true如果心跳有效
     */
    boolean handleHeartbeat(Probe probe);

    /**
     * 检查超时
     *
     * @param probe 探针对象
     * @return true如果已超时
     */
    boolean isTimeout(Probe probe);

    /**
     * 获取超时阈值（秒）
     *
     * @return 超时秒数
     */
    int getTimeoutSeconds();

    /**
     * 获取支持的探针类型
     *
     * @return 探针类型
     */
    String getSupportedType();
}
