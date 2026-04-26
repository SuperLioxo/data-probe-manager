package com.lixin.probe.enums;

import lombok.Getter;

/**
 * 探针类型枚举
 */
@Getter
public enum ProbeType {
    /**
     * 系统监控探针 - 通过UDP上报CPU/内存/磁盘/网络指标
     */
    SYSTEM("SYSTEM", "系统监控探针", "UDP"),

    /**
     * 数据库探针 - 通过WebSocket上报数据库元数据和性能指标
     */
    DATABASE("DATABASE", "数据库探针", "WebSocket"),

    /**
     * 文件探针 - 通过WebSocket上报文件扫描数据
     */
    FILE("FILE", "文件探针", "WebSocket");

    private final String code;
    private final String desc;
    private final String protocol;

    ProbeType(String code, String desc, String protocol) {
        this.code = code;
        this.desc = desc;
        this.protocol = protocol;
    }

    /**
     * 根据code获取枚举
     */
    public static ProbeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProbeType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
