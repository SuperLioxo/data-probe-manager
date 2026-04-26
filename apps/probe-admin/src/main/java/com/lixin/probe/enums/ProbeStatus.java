package com.lixin.probe.enums;

import lombok.Getter;

/**
 * 探针状态枚举
 */
@Getter
public enum ProbeStatus {
    /**
     * 在线
     */
    ONLINE("online", "在线"),

    /**
     * 离线
     */
    OFFLINE("offline", "离线"),

    /**
     * 异常
     */
    ERROR("error", "异常"),

    /**
     * 维护中
     */
    MAINTENANCE("maintenance", "维护中");

    private final String code;
    private final String desc;

    ProbeStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
