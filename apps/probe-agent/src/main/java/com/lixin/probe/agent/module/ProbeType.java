package com.lixin.probe.agent.module;

/**
 * 探针类型枚举
 */
public enum ProbeType {
    /**
     * 系统监控探针
     */
    SYSTEM,

    /**
     * 数据库探针
     */
    DATABASE,

    /**
     * 文件探针
     */
    FILE,

    /**
     * 未知类型
     */
    UNKNOWN
}
