package com.lixin.probe.constants;

/**
 * 系统常量定义
 * 避免魔法值，提高代码可维护性
 */
public final class SystemConstants {

    private SystemConstants() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }

    /**
     * 分页参数常量
     */
    public static final class Pagination {
        /** 默认页码 */
        public static final int DEFAULT_PAGE_NUM = 1;

        /** 默认每页数量 */
        public static final int DEFAULT_PAGE_SIZE = 10;

        /** 最大每页数量 */
        public static final int MAX_PAGE_SIZE = 1000;

        /** 最小每页数量 */
        public static final int MIN_PAGE_SIZE = 1;

        private Pagination() {}
    }

    /**
     * JWT相关常量
     */
    public static final class Jwt {
        /** Token前缀 */
        public static final String TOKEN_PREFIX = "Bearer ";

        /** Token默认过期时间（2小时，单位：毫秒） */
        public static final long DEFAULT_EXPIRATION = 7200000L;

        /** Token最小长度 */
        public static final int MIN_TOKEN_LENGTH = 50;

        /** 密钥最小长度（32字符 = 256位） */
        public static final int MIN_SECRET_LENGTH = 32;

        private Jwt() {}
    }

    /**
     * 用户相关常量
     */
    public static final class User {
        /** 用户名最小长度 */
        public static final int USERNAME_MIN_LENGTH = 3;

        /** 用户名最大长度 */
        public static final int USERNAME_MAX_LENGTH = 50;

        /** 密码最小长度 */
        public static final int PASSWORD_MIN_LENGTH = 6;

        /** 密码最大长度 */
        public static final int PASSWORD_MAX_LENGTH = 100;

        /** 真实姓名最大长度 */
        public static final int REAL_NAME_MAX_LENGTH = 50;

        /** 邮箱最大长度 */
        public static final int EMAIL_MAX_LENGTH = 100;

        /** 状态：正常 */
        public static final int STATUS_ACTIVE = 1;

        /** 状态：禁用 */
        public static final int STATUS_DISABLED = 0;

        private User() {}
    }

    /**
     * 角色相关常量
     */
    public static final class Role {
        /** 角色名称最小长度 */
        public static final int ROLE_NAME_MIN_LENGTH = 2;

        /** 角色名称最大长度 */
        public static final int ROLE_NAME_MAX_LENGTH = 50;

        /** 角色编码最大长度 */
        public static final int ROLE_CODE_MAX_LENGTH = 50;

        /** 角色描述最大长度 */
        public static final int DESCRIPTION_MAX_LENGTH = 200;

        /** 一次最多分配权限数量 */
        public static final int MAX_ASSIGN_PERMISSIONS = 100;

        private Role() {}
    }

    /**
     * 探针相关常量
     */
    public static final class Probe {
        /** 探针标识最小长度 */
        public static final int PROBE_KEY_MIN_LENGTH = 3;

        /** 探针标识最大长度 */
        public static final int PROBE_KEY_MAX_LENGTH = 50;

        /** 探针名称最小长度 */
        public static final int PROBE_NAME_MIN_LENGTH = 2;

        /** 探针名称最大长度 */
        public static final int PROBE_NAME_MAX_LENGTH = 100;

        /** 探针版本最大长度 */
        public static final int VERSION_MAX_LENGTH = 20;

        /** 配置信息最大长度 */
        public static final int CONFIG_MAX_LENGTH = 5000;

        /** 端口最小值 */
        public static final int PORT_MIN = 1;

        /** 端口最大值 */
        public static final int PORT_MAX = 65535;

        /** 采集间隔最小值（秒） */
        public static final int COLLECT_INTERVAL_MIN = 1;

        /** 采集间隔最大值（秒） */
        public static final int COLLECT_INTERVAL_MAX = 3600;

        private Probe() {}
    }

    /**
     * HTTP状态码常量
     */
    public static final class HttpStatus {
        /** 成功 */
        public static final int SUCCESS = 200;

        /** 创建 */
        public static final int CREATED = 201;

        /** 错误的请求 */
        public static final int BAD_REQUEST = 400;

        /** 未授权 */
        public static final int UNAUTHORIZED = 401;

        /** 禁止访问 */
        public static final int FORBIDDEN = 403;

        /** 未找到 */
        public static final int NOT_FOUND = 404;

        /** 冲突 */
        public static final int CONFLICT = 409;

        /** 服务器错误 */
        public static final int INTERNAL_SERVER_ERROR = 500;

        /** 服务不可用 */
        public static final int SERVICE_UNAVAILABLE = 503;

        private HttpStatus() {}
    }

    /**
     * 正则表达式常量
     */
    public static final class Regex {
        /** 用户名正则：字母、数字、下划线、@、点和连字符 */
        public static final String USERNAME = "^[a-zA-Z0-9_@.-]+$";

        /** 角色编码正则：大写字母和下划线 */
        public static final String ROLE_CODE = "^[A-Z_]+$";

        /** 手机号正则：中国手机号 */
        public static final String PHONE = "^1[3-9]\\d{9}$";

        /** 探针标识正则：字母、数字、下划线和连字符 */
        public static final String PROBE_KEY = "^[a-zA-Z0-9_-]+$";

        /** 探针类型正则 */
        public static final String PROBE_TYPE = "^(SYSTEM|FILE|DATABASE|UNIFIED)$";

        /** 探针状态正则 */
        public static final String PROBE_STATUS = "^(ONLINE|OFFLINE|DISABLED)$";

        /** IP地址正则 */
        public static final String IP_ADDRESS = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^$";

        private Regex() {}
    }

    /**
     * 时间常量
     */
    public static final class Time {
        /** 一分钟（毫秒） */
        public static final long ONE_MINUTE = 60 * 1000L;

        /** 一小时（毫秒） */
        public static final long ONE_HOUR = 60 * ONE_MINUTE;

        /** 一天（毫秒） */
        public static final long ONE_DAY = 24 * ONE_HOUR;

        /** 默认会话超时时间（30分钟） */
        public static final long SESSION_TIMEOUT = 30 * ONE_MINUTE;

        private Time() {}
    }

    /**
     * 文件相关常量
     */
    public static final class File {
        /** 最大文件大小（10MB） */
        public static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

        /** 允许的文件扩展名 */
        public static final String[] ALLOWED_EXTENSIONS = {
            ".jpg", ".jpeg", ".png", ".gif", ".bmp",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx",
            ".txt", ".csv", ".json", ".xml"
        };

        private File() {}
    }

    /**
     * 缓存相关常量
     */
    public static final class Cache {
        /** 用户缓存key前缀 */
        public static final String USER_PREFIX = "user:";

        /** 角色缓存key前缀 */
        public static final String ROLE_PREFIX = "role:";

        /** 权限缓存key前缀 */
        public static final String PERMISSION_PREFIX = "permission:";

        /** 默认缓存过期时间（30分钟） */
        public static final long DEFAULT_EXPIRE = 30 * 60L;

        private Cache() {}
    }

    /**
     * 业务限制常量
     */
    public static final class Limits {
        /** 单次批量操作最大数量 */
        public static final int MAX_BATCH_SIZE = 100;

        /** 导出数据最大行数 */
        public static final int MAX_EXPORT_ROWS = 10000;

        /** 用户名错误最大尝试次数 */
        public static final int MAX_LOGIN_ATTEMPTS = 5;

        private Limits() {}
    }
}
