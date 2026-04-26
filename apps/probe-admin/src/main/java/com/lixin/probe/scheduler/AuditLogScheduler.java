package com.lixin.probe.scheduler;

import com.lixin.probe.config.AuditLogProperties;
import com.lixin.probe.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计日志定时任务
 * 负责日志的自动清理和归档
 */
@Component
public class AuditLogScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditLogScheduler.class);

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogProperties properties;

    /**
     * 自动清理过期日志
     * 默认每天凌晨2点执行
     */
    @Scheduled(cron = "${audit-log.cleanup-cron:0 0 2 * * ?}")
    public void cleanupExpiredLogs() {
        if (!properties.isAutoCleanupEnabled()) {
            log.debug("审计日志自动清理未启用");
            return;
        }

        log.info("开始清理过期审计日志...");

        try {
            LocalDateTime cleanupDate = LocalDateTime.now()
                    .minusDays(properties.getRetentionDays());

            int cleanedCount = auditLogService.cleanupExpiredLogs(cleanupDate);

            log.info("审计日志清理完成，共清理 {} 条记录", cleanedCount);

        } catch (Exception e) {
            log.error("清理审计日志失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 自动归档日志
     * 默认每天凌晨3点执行
     */
    @Scheduled(cron = "${audit-log.archive-cron:0 0 3 * * ?}")
    public void archiveLogs() {
        if (!properties.isArchiveEnabled()) {
            log.debug("审计日志自动归档未启用");
            return;
        }

        log.info("开始归档审计日志...");

        try {
            LocalDateTime archiveDate = LocalDateTime.now()
                    .minusDays(properties.getArchiveBeforeDays());

            int archivedCount = auditLogService.archiveLogs(archiveDate);

            log.info("审计日志归档完成，共归档 {} 条记录", archivedCount);

        } catch (Exception e) {
            log.error("归档审计日志失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发清理任务
     * 可以通过管理接口调用
     */
    public int manualCleanup(int retentionDays) {
        log.info("手动触发审计日志清理，保留天数: {}", retentionDays);

        LocalDateTime cleanupDate = LocalDateTime.now().minusDays(retentionDays);
        return auditLogService.cleanupExpiredLogs(cleanupDate);
    }

    /**
     * 手动触发归档任务
     * 可以通过管理接口调用
     */
    public int manualArchive(int beforeDays) {
        log.info("手动触发审计日志归档，归档天数: {}", beforeDays);

        LocalDateTime archiveDate = LocalDateTime.now().minusDays(beforeDays);
        return auditLogService.archiveLogs(archiveDate);
    }
}
