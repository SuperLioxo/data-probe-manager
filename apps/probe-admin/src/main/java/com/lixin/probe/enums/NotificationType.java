package com.lixin.probe.enums;

/**
 * 通知类型枚举
 */
public enum NotificationType {
    /**
     * 邮件通知
     */
    EMAIL("EMAIL", "邮件通知"),

    /**
     * Webhook通知
     */
    WEBHOOK("WEBHOOK", "Webhook通知");

    private final String code;
    private final String description;

    NotificationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static NotificationType fromCode(String code) {
        for (NotificationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown notification type: " + code);
    }
}
