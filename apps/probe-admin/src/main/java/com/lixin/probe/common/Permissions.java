package com.lixin.probe.common;

/**
 * 权限常量定义
 * 定义系统中所有需要的权限字符串
 */
public class Permissions {
    /**
     * 探针管理权限
     */
    public static final String PROBE_VIEW = "probe:list";
    public static final String PROBE_CREATE = "probe:create";
    public static final String PROBE_UPDATE = "probe:update";
    public static final String PROBE_DELETE = "probe:delete";
    public static final String PROBE_CONTROL = "probe:control";

    /**
     * 监控数据权限
     */
    public static final String METRIC_VIEW = "metric:view";
    public static final String METRIC_EXPORT = "metric:export";

    /**
     * 告警权限
     */
    public static final String ALERT_VIEW = "alert:view";
    public static final String ALERT_HANDLE = "alert:handle";

    /**
     * 系统管理权限
     */
    public static final String SYSTEM_ADMIN = "system:admin";

    /**
     * 白名单管理权限
     */
    public static final String WHITELIST_MANAGE = "whitelist:manage";

    private Permissions() {
        // 防止实例化
    }
}
