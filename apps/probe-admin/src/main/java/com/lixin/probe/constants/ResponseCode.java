package com.lixin.probe.constants;

/**
 * API响应码枚举
 * 标准化API响应码和消息
 */
public enum ResponseCode {

    // ========== 成功响应 ==========

    /** 成功 */
    SUCCESS(200, "操作成功"),

    /** 创建成功 */
    CREATED(201, "创建成功"),

    // ========== 客户端错误 4xx ==========

    /** 请求参数错误 */
    BAD_REQUEST(400, "请求参数错误"),

    /** 未授权，需要登录 */
    UNAUTHORIZED(401, "未授权，请先登录"),

    /** 权限不足 */
    FORBIDDEN(403, "权限不足，无法访问"),

    /** 资源不存在 */
    NOT_FOUND(404, "请求的资源不存在"),

    /** 请求方法不支持 */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    /** 资源冲突 */
    CONFLICT(409, "资源冲突，操作失败"),

    /** 请求过于频繁 */
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    // ========== 服务端错误 5xx ==========

    /** 服务器内部错误 */
    INTERNAL_SERVER_ERROR(500, "服务器内部错误，请稍后重试"),

    /** 服务不可用 */
    SERVICE_UNAVAILABLE(503, "服务暂时不可用，请稍后重试"),

    // ========== 业务错误码 ==========

    /** 用户名或密码错误 */
    LOGIN_FAILED(1001, "用户名或密码错误"),

    /** 账户已被禁用 */
    ACCOUNT_DISABLED(1002, "账户已被禁用"),

    /** Token无效或已过期 */
    TOKEN_INVALID(1003, "Token无效或已过期"),

    /** 用户不存在 */
    USER_NOT_FOUND(1004, "用户不存在"),

    /** 角色不存在 */
    ROLE_NOT_FOUND(1005, "角色不存在"),

    /** 权限不存在 */
    PERMISSION_NOT_FOUND(1006, "权限不存在"),

    /** 探针不存在 */
    PROBE_NOT_FOUND(1007, "探针不存在"),

    /** 角色编码已存在 */
    ROLE_CODE_EXISTS(1008, "角色编码已存在"),

    /** 用户名已存在 */
    USERNAME_EXISTS(1009, "用户名已存在"),

    /** 探针标识已存在 */
    PROBE_KEY_EXISTS(1010, "探针标识已存在"),

    /** 角色还有用户关联，无法删除 */
    ROLE_HAS_USERS(1011, "该角色还有用户关联，无法删除"),

    /** 参数校验失败 */
    VALIDATION_FAILED(1012, "参数校验失败"),

    /** 数据已存在 */
    DATA_EXISTS(1013, "数据已存在"),

    /** 数据被引用，无法删除 */
    DATA_REFERENCED(1014, "数据被引用，无法删除"),

    /** 操作失败 */
    OPERATION_FAILED(1015, "操作失败"),

    /** 系统配置错误 */
    SYSTEM_CONFIG_ERROR(1016, "系统配置错误"),

    /** 数据库操作失败 */
    DATABASE_ERROR(1017, "数据库操作失败"),

    /** 缓存操作失败 */
    CACHE_ERROR(1018, "缓存操作失败"),

    /** 外部服务调用失败 */
    EXTERNAL_SERVICE_ERROR(1019, "外部服务调用失败"),

    /** 文件上传失败 */
    FILE_UPLOAD_FAILED(1020, "文件上传失败"),

    /** 文件下载失败 */
    FILE_DOWNLOAD_FAILED(1021, "文件下载失败"),

    /** 文件格式不支持 */
    FILE_FORMAT_NOT_SUPPORTED(1022, "文件格式不支持"),

    /** 文件大小超限 */
    FILE_SIZE_EXCEEDED(1023, "文件大小超过限制"),

    /** 导入数据格式错误 */
    IMPORT_DATA_FORMAT_ERROR(1024, "导入数据格式错误"),

    /** 导出数据失败 */
    EXPORT_DATA_FAILED(1025, "导出数据失败"),

    /** 批量操作部分成功 */
    BATCH_PARTIAL_SUCCESS(1026, "批量操作部分成功"),

    /** 批量操作全部失败 */
    BATCH_ALL_FAILED(1027, "批量操作全部失败");

    private final int code;
    private final String message;

    ResponseCode(int code, String message) {
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
     * 根据code获取枚举
     */
    public static ResponseCode fromCode(int code) {
        for (ResponseCode rc : values()) {
            if (rc.code == code) {
                return rc;
            }
        }
        return INTERNAL_SERVER_ERROR;
    }
}
