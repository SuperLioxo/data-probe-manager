package com.lixin.probe.timeseries;

import com.lixin.probe.dto.MetricData;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 时间序列数据库接口
 *
 * <p>统一不同时序数据库的访问方式，支持多种时间序列数据库实现。
 * 目标数据库包括：InfluxDB、Prometheus、TimescaleDB等。</p>
 *
 * <p>主要功能：
 * <ul>
 *   <li>写入指标数据</li>
 *   <li>查询指标数据</li>
 *   <li>清理旧数据</li>
 * </ul></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public interface TimeSeriesDatabase {

    /**
     * 写入指标数据
     *
     * <p>将探针采集的指标数据写入时间序列数据库。
     * 批量写入可以提高性能。</p>
     *
     * @param probeKey 探针标识
     * @param metrics 指标数据列表
     * @throws TimeSeriesException 写入失败时抛出
     */
    void writeMetrics(String probeKey, List<MetricData> metrics) throws TimeSeriesException;

    /**
     * 写入单条指标数据
     *
     * @param probeKey 探针标识
     * @param metric 指标数据
     * @throws TimeSeriesException 写入失败时抛出
     */
    default void writeMetric(String probeKey, MetricData metric) throws TimeSeriesException {
        writeMetrics(probeKey, List.of(metric));
    }

    /**
     * 查询指标数据
     *
     * <p>按时间范围查询指定探针的指标数据。</p>
     *
     * @param probeKey 探针标识
     * @param metricName 指标名称
     * @param start 开始时间
     * @param end 结束时间
     * @return 指标数据列表，按时间升序排列
     * @throws TimeSeriesException 查询失败时抛出
     */
    List<MetricData> queryMetrics(String probeKey, String metricName,
                                  LocalDateTime start, LocalDateTime end) throws TimeSeriesException;

    /**
     * 查询最新指标数据
     *
     * @param probeKey 探针标识
     * @param metricName 指标名称
     * @param limit 返回记录数限制
     * @return 指标数据列表，按时间降序排列
     * @throws TimeSeriesException 查询失败时抛出
     */
    List<MetricData> queryLatestMetrics(String probeKey, String metricName, int limit) throws TimeSeriesException;

    /**
     * 删除旧数据
     *
     * <p>清理指定时间之前的旧数据，用于数据保留策略。</p>
     *
     * @param probeKey 探针标识
     * @param before 删除此时间之前的数据
     * @throws TimeSeriesException 删除失败时抛出
     */
    void deleteOldData(String probeKey, LocalDateTime before) throws TimeSeriesException;

    /**
     * 获取数据库类型名称
     *
     * @return 数据库类型（如：influxdb, prometheus）
     */
    String getDatabaseType();

    /**
     * 检查数据库连接是否正常
     *
     * @return true如果连接正常，false如果连接失败
     */
    default boolean healthCheck() {
        return true;
    }
}
