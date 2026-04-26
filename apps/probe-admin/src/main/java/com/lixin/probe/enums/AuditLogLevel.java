package com.lixin.probe.enums;

/**
 * 审计日志级别枚举
 * 定义不同操作的重要程度
 */
public enum AuditLogLevel {
    /**
     * 信息级别 - 普通查询操作
     */
    INFO(0, "信息"),

    /**
     * 警告级别 - 可能存在风险的操作
     */
    WARN(1, "警告"),

    /**
     * 错误级别 - 操作失败或异常
     */
    ERROR(2, "错误"),

    /**
     * 关键级别 - 敏感操作（删除、权限变更等）
     */
    CRITICAL(3, "关键");

    private final int code;
    private final String description;

    AuditLogLevel(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举
     */
    public static AuditLogLevel fromCode(int code) {
        for (AuditLogLevel level : values()) {
            if (level.getCode() == code) {
                return level;
            }
        }
        return INFO;
    }
}
