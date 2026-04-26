package com.lixin.probe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审计日志配置属性
 */
@Data
@ConfigurationProperties(prefix = "audit-log")
public class AuditLogProperties {

    /**
     * 是否启用审计日志
     */
    private boolean enabled = true;

    /**
     * 是否异步记录日志
     */
    private boolean async = true;

    /**
     * 日志保留天数
     */
    private int retentionDays = 90;

    /**
     * 是否启用自动清理
     */
    private boolean autoCleanupEnabled = false;

    /**
     * 自动清理执行时间（Cron表达式）
     */
    private String cleanupCron = "0 0 2 * * ?";

    /**
     * 是否启用日志归档
     */
    private boolean archiveEnabled = false;

    /**
     * 归档执行时间（Cron表达式）
     */
    private String archiveCron = "0 0 3 * * ?";

    /**
     * 归档前天数（超过此天数的日志将被归档）
     */
    private int archiveBeforeDays = 30;

    /**
     * 归档文件存储路径
     */
    private String archivePath = "/var/log/probe/audit-archive";

    /**
     * 是否压缩归档文件
     */
    private boolean compressArchive = true;

    /**
     * 最大归档文件大小（MB）
     */
    private long maxArchiveFileSize = 100;

    /**
     * 敏感参数名称（不记录这些参数的值）
     */
    private String[] sensitiveParams = {"password", "pwd", "secret", "token", "key"};

    /**
     * 最大请求参数长度（超过则截断）
     */
    private int maxParamLength = 2000;

    /**
     * 是否记录查询操作
     */
    private boolean logQueryOperations = false;

    /**
     * 是否记录成功操作
     */
    private boolean logSuccessOperations = true;

    /**
     * 是否记录失败操作
     */
    private boolean logFailedOperations = true;
}
