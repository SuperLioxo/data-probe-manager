package com.lixin.probe.enums;

/**
 * 审计日志操作类型枚举
 */
public enum AuditLogOperation {
    /**
     * 创建操作
     */
    CREATE("CREATE", "创建"),

    /**
     * 更新操作
     */
    UPDATE("UPDATE", "更新"),

    /**
     * 删除操作
     */
    DELETE("DELETE", "删除"),

    /**
     * 查询操作
     */
    QUERY("QUERY", "查询"),

    /**
     * 登录操作
     */
    LOGIN("LOGIN", "登录"),

    /**
     * 登出操作
     */
    LOGOUT("LOGOUT", "登出"),

    /**
     * 导出操作
     */
    EXPORT("EXPORT", "导出"),

    /**
     * 导入操作
     */
    IMPORT("IMPORT", "导入"),

    /**
     * 批量操作
     */
    BATCH("BATCH", "批量操作"),

    /**
     * 权限变更操作
     */
    PERMISSION_CHANGE("PERMISSION_CHANGE", "权限变更"),

    /**
     * 配置变更操作
     */
    CONFIG_CHANGE("CONFIG_CHANGE", "配置变更"),

    /**
     * 其他操作
     */
    OTHER("OTHER", "其他");

    private final String code;
    private final String description;

    AuditLogOperation(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举
     */
    public static AuditLogOperation fromCode(String code) {
        for (AuditLogOperation operation : values()) {
            if (operation.getCode().equals(code)) {
                return operation;
            }
        }
        return OTHER;
    }

    /**
     * 根据方法名推断操作类型
     */
    public static AuditLogOperation inferFromMethod(String methodName) {
        if (methodName == null) {
            return OTHER;
        }

        String lowerMethod = methodName.toLowerCase();

        if (lowerMethod.startsWith("create") || lowerMethod.startsWith("add") ||
            lowerMethod.startsWith("save") || lowerMethod.startsWith("insert")) {
            return CREATE;
        } else if (lowerMethod.startsWith("update") || lowerMethod.startsWith("modify") ||
                   lowerMethod.startsWith("edit") || lowerMethod.startsWith("change")) {
            return UPDATE;
        } else if (lowerMethod.startsWith("delete") || lowerMethod.startsWith("remove") ||
                   lowerMethod.startsWith("drop")) {
            return DELETE;
        } else if (lowerMethod.startsWith("get") || lowerMethod.startsWith("query") ||
                   lowerMethod.startsWith("list") || lowerMethod.startsWith("find") ||
                   lowerMethod.startsWith("search") || lowerMethod.startsWith("fetch")) {
            return QUERY;
        } else if (lowerMethod.contains("login") || lowerMethod.contains("signin")) {
            return LOGIN;
        } else if (lowerMethod.contains("logout") || lowerMethod.contains("signout")) {
            return LOGOUT;
        } else if (lowerMethod.contains("export")) {
            return EXPORT;
        } else if (lowerMethod.contains("import")) {
            return IMPORT;
        } else if (lowerMethod.contains("batch")) {
            return BATCH;
        }

        return OTHER;
    }
}
