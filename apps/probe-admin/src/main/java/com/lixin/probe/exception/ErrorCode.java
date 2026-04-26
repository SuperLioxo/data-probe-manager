package com.lixin.probe.exception;

/**
 * 错误码枚举
 * 统一管理系统中所有错误码
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 1.0
 */
public enum ErrorCode {

    // ========== 通用错误码 (1xxx) ==========
    SUCCESS(1000, "操作成功"),
    SYSTEM_ERROR(1001, "系统内部错误"),
    INVALID_PARAMETER(1002, "参数错误"),
    METHOD_NOT_SUPPORTED(1003, "请求方法不支持"),
    MEDIA_TYPE_NOT_SUPPORTED(1004, "媒体类型不支持"),
    METHOD_NOT_ALLOWED(1005, "请求方法不允许"),
    UNSUPPORTED_MEDIA_TYPE(1006, "不支持的媒体类型"),
    NOT_FOUND(1007, "资源不存在"),
    DATA_DUPLICATE(1008, "数据重复"),
    DATA_INTEGRITY_VIOLATION(1009, "数据完整性违规"),
    DATABASE_ERROR(1010, "数据库错误"),
    PROBE_KEY_DUPLICATE(1011, "探针标识已存在"),
    USERNAME_DUPLICATE(1012, "用户名已存在"),
    ROLE_CODE_DUPLICATE(1013, "角色编码已存在"),

    // ========== 认证授权错误码 (2xxx) ==========
    UNAUTHORIZED(2001, "未授权，请先登录"),
    TOKEN_EXPIRED(2002, "Token已过期"),
    TOKEN_INVALID(2003, "Token无效"),
    PERMISSION_DENIED(2003, "权限不足"),
    ACCOUNT_LOCKED(2004, "账户已锁定"),
    ACCOUNT_DISABLED(2005, "账户已禁用"),

    // ========== 探针管理错误码 (3xxx) ==========
    PROBE_NOT_FOUND(3001, "探针不存在"),
    PROBE_KEY_EXISTS(3002, "探针Key已存在"),
    PROBE_SYSTEM_IP_EXISTS(3008, "该IP地址已存在系统探针"),
    PROBE_OFFLINE(3003, "探针离线"),
    PROBE_BUSY(3004, "探针忙碌"),
    PROBE_INVALID_TYPE(3005, "探针类型无效"),
    PROBE_CONFIG_INVALID(3006, "探针配置无效"),
    PROBE_HEARTBEAT_TIMEOUT(3007, "探针心跳超时"),

    // ========== 文件探针错误码 (31xx) ==========
    FILE_PROBE_NOT_FOUND(3101, "文件探针不存在"),
    FILE_PATH_INVALID(3102, "文件路径无效"),
    FILE_NOT_FOUND(3103, "文件不存在"),
    FILE_SCAN_FAILED(3104, "文件扫描失败"),
    FILE_METADATA_ERROR(3105, "文件元数据错误"),

    // ========== 数据库探针错误码 (32xx) ==========
    DB_PROBE_NOT_FOUND(3201, "数据库探针不存在"),
    DB_CONNECTION_FAILED(3202, "数据库连接失败"),
    DB_QUERY_FAILED(3203, "数据库查询失败"),
    DB_METADATA_ERROR(3204, "数据库元数据错误"),

    // ========== 探针组错误码 (33xx) ==========
    PROBE_GROUP_NOT_FOUND(3301, "探针组不存在"),
    PROBE_GROUP_NAME_EXISTS(3302, "探针组名称已存在"),
    PROBE_ALREADY_IN_GROUP(3303, "探针已在组中"),
    PROBE_NOT_IN_GROUP(3304, "探针不在组中"),

    // ========== 插件管理错误码 (35xx) ==========
    PLUGIN_NOT_FOUND(3501, "插件不存在"),
    AGENT_OFFLINE(3502, "Agent离线"),
    PLUGIN_COMMAND_FAILED(3503, "插件命令执行失败"),
    PLUGIN_LOAD_FAILED(3504, "插件加载失败"),
    PLUGIN_UNLOAD_FAILED(3505, "插件卸载失败"),
    PLUGIN_RELOAD_FAILED(3506, "插件重载失败"),
    PLUGIN_STATUS_INVALID(3507, "插件状态无效"),
    AGENT_NOT_FOUND(3508, "Agent不存在"),
    PLUGIN_HEARTBEAT_TIMEOUT(3509, "插件心跳超时"),
    PLUGIN_ALREADY_LOADED(3510, "插件已加载"),
    PLUGIN_NOT_LOADED(3511, "插件未加载"),

    // ========== 用户权限错误码 (4xxx) ==========
    USER_NOT_FOUND(4001, "用户不存在"),
    USER_EXISTS(4002, "用户已存在"),
    USER_PASSWORD_WRONG(4003, "密码错误"),
    ROLE_NOT_FOUND(4004, "角色不存在"),
    ROLE_EXISTS(4005, "角色已存在"),
    PERMISSION_NOT_FOUND(4006, "权限不存在"),
    USER_ALREADY_HAS_ROLE(4007, "用户已拥有该角色"),
    USER_NOT_HAS_ROLE(4008, "用户不拥有该角色"),

    // ========== 告警管理错误码 (5xxx) ==========
    ALERT_NOT_FOUND(5001, "告警不存在"),
    ALERT_RULE_NOT_FOUND(5002, "告警规则不存在"),
    ALERT_RULE_NAME_EXISTS(5003, "告警规则名称已存在"),
    ALERT_THRESHOLD_INVALID(5004, "告警阈值无效"),
    NOTIFICATION_FAILED(5005, "通知发送失败"),
    NOTIFICATION_CHANNEL_NOT_FOUND(5006, "通知渠道不存在"),

    // ========== 审计日志错误码 (6xxx) ==========
    AUDIT_LOG_NOT_FOUND(6001, "审计日志不存在"),
    AUDIT_LOG_EXPORT_FAILED(6002, "审计日志导出失败"),

    // ========== WebSocket错误码 (7xxx) ==========
    WEBSOCKET_CONNECTION_FAILED(7001, "WebSocket连接失败"),
    WEBSOCKET_SEND_FAILED(7002, "WebSocket消息发送失败"),
    WEBSOCKET_SESSION_NOT_FOUND(7003, "WebSocket会话不存在"),
    WEBSOCKET_HANDSHAKE_FAILED(7004, "WebSocket握手失败"),

    // ========== 文件操作错误码 (8xxx) ==========
    FILE_UPLOAD_FAILED(8001, "文件上传失败"),
    FILE_DOWNLOAD_FAILED(8002, "文件下载失败"),
    FILE_DELETE_FAILED(8003, "文件删除失败"),
    FILE_TYPE_INVALID(8004, "文件类型无效"),
    FILE_SIZE_EXCEEDED(8005, "文件大小超限"),
    FILE_NAME_INVALID(8006, "文件名无效"),

    // ========== 配置错误码 (9xxx) ==========
    CONFIG_NOT_FOUND(9001, "配置不存在"),
    CONFIG_INVALID(9002, "配置无效"),
    CONFIG_UPDATE_FAILED(9003, "配置更新失败");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误描述
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 根据错误码获取枚举
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
