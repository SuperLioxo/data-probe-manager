package com.lixin.probe.timeseries;

import com.lixin.probe.dto.MetricData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 时间序列数据库使用示例
 *
 * <p>演示如何使用适配器模式统一操作不同类型的时间序列数据库。
 * 实际使用时可以将此逻辑集成到ProbeDataService中。</p>
 *
 * <p>支持的数据库：
 * <ul>
 *   <li>InfluxDB - 配置 tsdb.type=influxdb</li>
 *   <li>Prometheus - 配置 tsdb.type=prometheus</li>
 * </ul></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
public class TimeSeriesExample {

    private static final Logger log = LoggerFactory.getLogger(TimeSeriesExample.class);

    @Autowired(required = false)
    private TimeSeriesDatabase timeSeriesDatabase;

    /**
     * 保存探针指标数据
     *
     * @param probeKey 探针标识
     * @param metrics 指标数据列表
     */
    public void saveMetrics(String probeKey, List<MetricData> metrics) {
        if (timeSeriesDatabase == null) {
            log.warn("时间序列数据库未配置，跳过保存: probeKey={}", probeKey);
            return;
        }

        try {
            timeSeriesDatabase.writeMetrics(probeKey, metrics);
            log.info("保存指标成功: probeKey={}, count={}, dbType={}",
                    probeKey, metrics.size(), timeSeriesDatabase.getDatabaseType());
        } catch (TimeSeriesException e) {
            log.error("保存指标失败: probeKey={}", probeKey, e);
        }
    }

    /**
     * 查询探针指标数据
     *
     * @param probeKey 探针标识
     * @param metricName 指标名称
     * @param start 开始时间
     * @param end 结束时间
     * @return 指标数据列表
     */
    public List<MetricData> queryMetrics(String probeKey, String metricName,
                                         LocalDateTime start, LocalDateTime end) {
        if (timeSeriesDatabase == null) {
            log.warn("时间序列数据库未配置: probeKey={}", probeKey);
            return List.of();
        }

        try {
            List<MetricData> metrics = timeSeriesDatabase.queryMetrics(
                    probeKey, metricName, start, end);
            log.info("查询指标成功: probeKey={}, metricName={}, count={}",
                    probeKey, metricName, metrics.size());
            return metrics;
        } catch (TimeSeriesException e) {
            log.error("查询指标失败: probeKey={}, metricName={}", probeKey, metricName, e);
            return List.of();
        }
    }

    /**
     * 查询最新指标数据
     *
     * @param probeKey 探针标识
     * @param metricName 指标名称
     * @param limit 返回记录数
     * @return 指标数据列表
     */
    public List<MetricData> queryLatestMetrics(String probeKey, String metricName, int limit) {
        if (timeSeriesDatabase == null) {
            log.warn("时间序列数据库未配置: probeKey={}", probeKey);
            return List.of();
        }

        try {
            List<MetricData> metrics = timeSeriesDatabase.queryLatestMetrics(
                    probeKey, metricName, limit);
            log.info("查询最新指标成功: probeKey={}, metricName={}, count={}",
                    probeKey, metricName, metrics.size());
            return metrics;
        } catch (TimeSeriesException e) {
            log.error("查询最新指标失败: probeKey={}, metricName={}", probeKey, metricName, e);
            return List.of();
        }
    }

    /**
     * 清理旧数据
     *
     * @param probeKey 探针标识
     * @param daysToKeep 保留天数
     */
    public void cleanupOldData(String probeKey, int daysToKeep) {
        if (timeSeriesDatabase == null) {
            log.warn("时间序列数据库未配置: probeKey={}", probeKey);
            return;
        }

        try {
            LocalDateTime before = LocalDateTime.now().minusDays(daysToKeep);
            timeSeriesDatabase.deleteOldData(probeKey, before);
            log.info("清理旧数据成功: probeKey={}, before={}", probeKey, before);
        } catch (TimeSeriesException e) {
            log.error("清理旧数据失败: probeKey={}", probeKey, e);
        }
    }

    /**
     * 健康检查
     *
     * @return true如果数据库连接正常
     */
    public boolean healthCheck() {
        if (timeSeriesDatabase == null) {
            log.warn("时间序列数据库未配置");
            return false;
        }

        boolean healthy = timeSeriesDatabase.healthCheck();
        log.info("时间序列数据库健康检查: dbType={}, healthy={}",
                timeSeriesDatabase.getDatabaseType(), healthy);
        return healthy;
    }
}
