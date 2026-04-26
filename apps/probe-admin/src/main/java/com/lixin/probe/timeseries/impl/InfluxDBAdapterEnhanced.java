package com.lixin.probe.timeseries.impl;

import com.lixin.probe.dto.MetricData;
import com.lixin.probe.service.InfluxDBService;
import com.lixin.probe.timeseries.TimeSeriesDatabase;
import com.lixin.probe.timeseries.TimeSeriesException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * InfluxDB增强适配器
 *
 * <p>基于InfluxDBService的增强实现，提供完整的TimeSeriesDatabase接口功能。</p>
 *
 * <p>特性：</p>
 * <ul>
 *   <li>自动重试机制</li>
 *   <li>批量异步写入</li>
 *   <li>聚合查询支持</li>
 *   <li>数据清理功能</li>
 *   <li>健康检查</li>
 * </ul>
 *
 * @author Claude Code
 * @since 1.0
 * @version 2.0
 */
@Component
@ConditionalOnProperty(name = "influx.enabled", havingValue = "true", matchIfMissing = false)
public class InfluxDBAdapterEnhanced implements TimeSeriesDatabase {

    private static final Logger log = LoggerFactory.getLogger(InfluxDBAdapterEnhanced.class);

    private final InfluxDBService influxDBService;

    public InfluxDBAdapterEnhanced(InfluxDBService influxDBService) {
        this.influxDBService = influxDBService;
        log.info("InfluxDB增强适配器已初始化");
    }

    @Override
    public void writeMetrics(String probeKey, List<MetricData> metrics) throws TimeSeriesException {
        if (metrics == null || metrics.isEmpty()) {
            log.warn("指标数据为空，跳过写入: probeKey={}", probeKey);
            return;
        }

        try {
            // 使用同步写入（自动重试）
            int count = influxDBService.writeMetricsSync(metrics);
            log.debug("写入InfluxDB成功: probeKey={}, count={}", probeKey, count);

        } catch (Exception e) {
            log.error("写入InfluxDB失败: probeKey={}", probeKey, e);
            throw new TimeSeriesException("写入InfluxDB失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MetricData> queryMetrics(String probeKey, String metricName,
                                         LocalDateTime start, LocalDateTime end) throws TimeSeriesException {
        try {
            return influxDBService.queryRawData(probeKey, metricName, start, end);

        } catch (Exception e) {
            log.error("查询InfluxDB失败: probeKey={}, metricName={}", probeKey, metricName, e);
            throw new TimeSeriesException("查询InfluxDB失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MetricData> queryLatestMetrics(String probeKey, String metricName, int limit) throws TimeSeriesException {
        try {
            return influxDBService.queryLatest(probeKey, metricName, limit);

        } catch (Exception e) {
            log.error("查询最新数据失败: probeKey={}, metricName={}", probeKey, metricName, e);
            throw new TimeSeriesException("查询最新数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteOldData(String probeKey, LocalDateTime before) throws TimeSeriesException {
        try {
            influxDBService.deleteOldData(probeKey, before);
            log.info("删除旧数据成功: probeKey={}, before={}", probeKey, before);

        } catch (Exception e) {
            log.error("删除旧数据失败: probeKey={}, before={}", probeKey, before, e);
            throw new TimeSeriesException("删除旧数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDatabaseType() {
        return "influxdb";
    }

    @Override
    public boolean healthCheck() {
        return influxDBService.healthCheck();
    }

    // ========== 扩展方法 ==========

    /**
     * 异步写入指标数据
     *
     * @param probeKey 探针标识
     * @param metrics 指标数据列表
     * @return CompletableFuture
     */
    public java.util.concurrent.CompletableFuture<Integer> writeMetricsAsync(
        String probeKey, List<MetricData> metrics) {

        if (metrics == null || metrics.isEmpty()) {
            log.warn("指标数据为空，跳过写入: probeKey={}", probeKey);
            return java.util.concurrent.CompletableFuture.completedFuture(0);
        }

        return influxDBService.writeMetricsAsync(metrics)
            .thenApply(count -> {
                log.debug("异步写入InfluxDB成功: probeKey={}, count={}", probeKey, count);
                return count;
            })
            .exceptionally(e -> {
                log.error("异步写入InfluxDB失败: probeKey={}", probeKey, e);
                throw new TimeSeriesException("异步写入InfluxDB失败: " + e.getMessage(), e);
            });
    }

    /**
     * 聚合查询
     *
     * @param probeKey 探针标识
     * @param metricName 指标名称
     * @param start 开始时间
     * @param end 结束时间
     * @param aggregateType 聚合类型（mean, max, min, sum, count）
     * @param window 时间窗口（如：1m, 5m, 1h, 1d）
     * @return 聚合后的指标数据列表
     */
    public List<MetricData> queryAggregate(String probeKey, String metricName,
                                          LocalDateTime start, LocalDateTime end,
                                          String aggregateType, String window) {
        try {
            return influxDBService.queryAggregate(probeKey, metricName, start, end,
                aggregateType, window);

        } catch (Exception e) {
            log.error("聚合查询失败: probeKey={}, metricName={}, aggregateType={}",
                probeKey, metricName, aggregateType, e);
            throw new TimeSeriesException("聚合查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分组聚合查询
     *
     * @param probeKey 探针标识
     * @param metricName 指标名称
     * @param start 开始时间
     * @param end 结束时间
     * @param groupByTag 分组标签（如：host, region）
     * @param aggregateType 聚合类型
     * @param window 时间窗口
     * @return 分组聚合后的指标数据列表
     */
    public List<MetricData> queryGroupBy(String probeKey, String metricName,
                                        LocalDateTime start, LocalDateTime end,
                                        String groupByTag,
                                        String aggregateType, String window) {
        try {
            return influxDBService.queryGroupBy(probeKey, metricName, start, end,
                groupByTag, aggregateType, window);

        } catch (Exception e) {
            log.error("分组聚合查询失败: probeKey={}, metricName={}, groupBy={}",
                probeKey, metricName, groupByTag, e);
            throw new TimeSeriesException("分组聚合查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取数据库统计信息
     *
     * @return 统计信息Map
     */
    public java.util.Map<String, Object> getStatistics() {
        return influxDBService.getStatistics();
    }
}
