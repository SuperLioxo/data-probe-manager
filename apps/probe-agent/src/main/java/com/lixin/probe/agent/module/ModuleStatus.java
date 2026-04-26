package com.lixin.probe.agent.module;

/**
 * 模块状态枚举
 */
public enum ModuleStatus {
    /**
     * 初始化中
     */
    INITIALIZING,

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 已停止
     */
    STOPPED,

    /**
     * 错误状态
     */
    ERROR,

    /**
     * 禁用状态
     */
    DISABLED
}
