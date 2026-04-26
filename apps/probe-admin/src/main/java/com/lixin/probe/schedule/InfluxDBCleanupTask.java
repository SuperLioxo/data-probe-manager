package com.lixin.probe.schedule;

import com.lixin.probe.service.ProbeService;
import com.lixin.probe.timeseries.TimeSeriesException;
import com.lixin.probe.timeseries.impl.InfluxDBAdapterEnhanced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * InfluxDB数据清理定时任务
 *
 * <p>定期清理过期的时序数据，实现数据保留策略。</p>
 *
 * <p>配置示例：</p>
 * <pre>
 * influx:
 *   cleanup:
 *     enabled: true
 *     retention-days: 90
 *     cron: "0 0 2 * * ?"
 * </pre>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
@ConditionalOnBean(InfluxDBAdapterEnhanced.class)
@ConditionalOnProperty(name = "influx.cleanup.enabled", havingValue = "true", matchIfMissing = false)
public class InfluxDBCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(InfluxDBCleanupTask.class);

    @Autowired(required = false)
    private InfluxDBAdapterEnhanced influxDBAdapter;

    @Autowired(required = false)
    private ProbeService probeService;

    /**
     * 每天凌晨2点执行数据清理
     */
    @Scheduled(cron = "${influx.cleanup.cron:0 0 2 * * ?}")
    public void cleanupOldMetrics() {
        if (influxDBAdapter == null) {
            log.warn("InfluxDB适配器未初始化，跳过数据清理");
            return;
        }

        log.info("开始执行InfluxDB数据清理任务");

        try {
            // 获取保留天数配置
            int retentionDays = getRetentionDays();
            LocalDateTime retention = LocalDateTime.now().minusDays(retentionDays);

            log.info("清理{}天之前的旧数据（{}之前）", retentionDays, retention);

            // 获取所有探针
            List<String> probeKeys = getAllProbeKeys();

            if (probeKeys.isEmpty()) {
                log.info("没有探针需要清理");
                return;
            }

            // 清理每个探针的旧数据
            int successCount = 0;
            int failureCount = 0;

            for (String probeKey : probeKeys) {
                try {
                    influxDBAdapter.deleteOldData(probeKey, retention);
                    successCount++;
                    log.info("成功清理探{}的旧数据", probeKey);

                } catch (TimeSeriesException e) {
                    failureCount++;
                    log.error("清理探{}的旧数据失败", probeKey, e);
                }
            }

            log.info("数据清理完成：成功{}个，失败{}个", successCount, failureCount);

        } catch (Exception e) {
            log.error("执行InfluxDB数据清理任务失败", e);
        }
    }

    /**
     * 每周日凌晨3点执行统计信息收集
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void collectStatistics() {
        if (influxDBAdapter == null) {
            return;
        }

        log.info("开始收集InfluxDB统计信息");

        try {
            var stats = influxDBAdapter.getStatistics();

            log.info("InfluxDB统计信息：");
            log.info("  24小时数据点总数: {}", stats.get("totalPoints24h"));
            log.info("  Measurement数量: {}", stats.get("measurementCount"));
            log.info("  健康状态: {}", stats.get("healthy"));
            log.info("  存储桶: {}", stats.get("bucket"));
            log.info("  组织: {}", stats.get("org"));

            // 这里可以将统计信息发送到监控系统或保存到数据库
            // sendToMonitoringSystem(stats);

        } catch (Exception e) {
            log.error("收集InfluxDB统计信息失败", e);
        }
    }

    /**
     * 每小时检查一次InfluxDB健康状态
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkHealth() {
        if (influxDBAdapter == null) {
            return;
        }

        try {
            boolean healthy = influxDBAdapter.healthCheck();

            if (healthy) {
                log.debug("InfluxDB健康检查：正常");
            } else {
                log.warn("InfluxDB健康检查：异常");
                // 这里可以发送告警通知
                // sendAlert("InfluxDB健康检查失败");
            }

        } catch (Exception e) {
            log.error("InfluxDB健康检查失败", e);
        }
    }

    /**
     * 获取保留天数配置
     */
    private int getRetentionDays() {
        // 从配置文件读取，默认90天
        // 实际项目中可以通过@Value注入
        return 90;
    }

    /**
     * 获取所有探针标识
     */
    private List<String> getAllProbeKeys() {
        try {
            if (probeService != null) {
                // 从ProbeService获取所有探针
                return probeService.getAllProbeKeys();
            }
        } catch (Exception e) {
            log.error("获取探针列表失败", e);
        }

        // 如果无法获取，返回空列表
        return List.of();
    }
}
