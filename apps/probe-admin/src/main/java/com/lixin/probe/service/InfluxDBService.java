package com.lixin.probe.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.lixin.probe.config.InfluxDBConfig;
import com.lixin.probe.dto.MetricData;
import com.lixin.probe.timeseries.TimeSeriesException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * InfluxDB服务层
 *
 * <p>提供InfluxDB的高级功能，包括：</p>
 * <ul>
 *   <li>批量异步写入</li>
 *   <li>聚合查询（AVG、MAX、MIN、SUM、COUNT）</li>
 *   <li>数据清理和保留策略</li>
 *   <li>重试机制</li>
 *   <li>健康检查</li>
 * </ul>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Service
@ConditionalOnProperty(name = "influx.enabled", havingValue = "true", matchIfMissing = false)
public class InfluxDBService {

    private static final Logger log = LoggerFactory.getLogger(InfluxDBService.class);

    private final InfluxDBClient influxDBClient;
    private final InfluxDBConfig config;
    private final ExecutorService executorService;

    public InfluxDBService(InfluxDBClient influxDBClient, InfluxDBConfig config) {
        this.influxDBClient = influxDBClient;
        this.config = config;
        this.executorService = Executors.newFixedThreadPool(4); // 异步写入线程池
    }

    /**
     * 批量写入指标数据（同步）
     *
     * @param metrics 指标数据列表
     * @return 成功写入的条数
     */
    public int writeMetricsSync(List<MetricData> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            log.warn("指标数据为空，跳过写入");
            return 0;
        }

        try {
            List<Point> points = metrics.stream()
                .map(this::convertToPoint)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (points.isEmpty()) {
                log.warn("没有有效的Point可写入");
                return 0;
            }

            // 使用重试机制写入
            int count = writeWithRetry(points);
            log.debug("同步写入InfluxDB成功: count={}", count);
            return count;

        } catch (Exception e) {
            log.error("同步写入InfluxDB失败", e);
            throw new TimeSeriesException("写入InfluxDB失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量写入指标数据（异步）
     *
     * @param metrics 指标数据列表
     * @return CompletableFuture，包含成功写入的条数
     */
    public CompletableFuture<Integer> writeMetricsAsync(List<MetricData> metrics) {
        return CompletableFuture.supplyAsync(() -> writeMetricsSync(metrics), executorService);
    }

    /**
     * 查询原始数据
     *
     * @param probeKey 探针标识
     * @param metricName 指标名称
     * @param start 开始时间
     * @param end 结束时间
     * @return 指标数据列表
     */
    public List<MetricData> queryRawData(String probeKey, String metricName,
                                         LocalDateTime start, LocalDateTime end) {
        try {
            String query = buildQuery(
                probeKey, metricName, start, end,
                null, null, null
            );

            return executeQuery(query);

        } catch (Exception e) {
            log.error("查询原始数据失败: probeKey={}, metricName={}", probeKey, metricName, e);
            throw new TimeSeriesException("查询失败: " + e.getMessage(), e);
        }
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
            String query = buildQuery(
                probeKey, metricName, start, end,
                aggregateType, window, null
            );

            return executeQuery(query);

        } catch (Exception e) {
            log.error("聚合查询失败: probeKey={}, metricName={}, aggregateType={}",
                probeKey, metricName, aggregateType, e);
            throw new TimeSeriesException("聚合查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分组查询
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
            String query = buildQuery(
                probeKey, metricName, start, end,
                aggregateType, window, groupByTag
            );

            return executeQuery(query);

        } catch (Exception e) {
            log.error("分组查询失败: probeKey={}, metricName={}, groupBy={}",
                probeKey, metricName, groupByTag, e);
            throw new TimeSeriesException("分组查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询最新数据
     *
     * @param probeKey 探针标识
     * @param metricName 指标名称
     * @param limit 返回记录数
     * @return 指标数据列表
     */
    public List<MetricData> queryLatest(String probeKey, String metricName, int limit) {
        try {
            String query = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -1h) " +
                "|> filter(fn: (r) => r._measurement == \"%s\") " +
                "|> filter(fn: (r) => r.probe_key == \"%s\") " +
                "|> sort(columns: [\"_time\"], desc: true) " +
                "|> limit(n: %d)",
                config.getBucket(), metricName, probeKey, limit
            );

            return executeQuery(query);

        } catch (Exception e) {
            log.error("查询最新数据失败: probeKey={}, metricName={}", probeKey, metricName, e);
            throw new TimeSeriesException("查询最新数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除旧数据
     *
     * @param probeKey 探针标识
     * @param before 删除此时间之前的数据
     */
    public void deleteOldData(String probeKey, LocalDateTime before) {
        try {
            var start = before.minusYears(10).atZone(ZoneId.systemDefault()).toOffsetDateTime();
            var stop = before.atZone(ZoneId.systemDefault()).toOffsetDateTime();

            var request = new com.influxdb.client.domain.DeletePredicateRequest()
                .start(start)
                .stop(stop)
                .predicate("_measurement=\"" + probeKey + "\"");

            influxDBClient.getDeleteApi().delete(request, config.getBucket(), config.getOrg());

            log.info("删除旧数据成功: probeKey={}, before={}", probeKey, before);

        } catch (Exception e) {
            log.error("删除旧数据失败: probeKey={}, before={}", probeKey, before, e);
            throw new TimeSeriesException("删除旧数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 健康检查
     *
     * @return 是否健康
     */
    public boolean healthCheck() {
        try {
            var health = influxDBClient.health();
            return health.getStatus().toString().equals("pass");
        } catch (Exception e) {
            log.error("InfluxDB健康检查失败", e);
            return false;
        }
    }

    /**
     * 获取数据库统计信息
     *
     * @return 统计信息Map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 查询数据点总数（最近24小时）
            String countQuery = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -24h) " +
                "|> count() " +
                "|> group() " +
                "|> sum()",
                config.getBucket()
            );

            List<FluxTable> tables = influxDBClient.getQueryApi().query(countQuery, config.getOrg());
            long totalCount = 0;
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Object value = record.getValueByKey("_value");
                    if (value != null) {
                        totalCount += ((Number) value).longValue();
                    }
                }
            }
            stats.put("totalPoints24h", totalCount);

            // 查询measurement数量
            String measurementQuery = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -24h) " +
                "|> keep(columns: [\"_measurement\"]) " +
                "|> distinct(column: \"_measurement\")",
                config.getBucket()
            );

            List<FluxTable> measurementTables = influxDBClient.getQueryApi()
                .query(measurementQuery, config.getOrg());
            int measurementCount = 0;
            for (FluxTable table : measurementTables) {
                measurementCount += table.getRecords().size();
            }
            stats.put("measurementCount", measurementCount);

            // 健康状态
            stats.put("healthy", healthCheck());
            stats.put("bucket", config.getBucket());
            stats.put("org", config.getOrg());

        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            log.info("InfluxDBService已关闭");
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ========== 私有方法 ==========

    /**
     * 转换MetricData为Point
     */
    private Point convertToPoint(MetricData metric) {
        if (metric == null || metric.getTimestamp() == null) {
            return null;
        }

        try {
            Point point = Point.measurement(metric.getMetricName())
                .addTag("probe_key", metric.getProbeKey())
                .time(metric.getTimestamp().atZone(ZoneId.systemDefault()).toInstant(),
                    WritePrecision.NS)
                .addField("value", metric.getValue());

            // 添加自定义标签
            if (metric.getTags() != null) {
                metric.getTags().forEach(point::addTag);
            }

            return point;

        } catch (Exception e) {
            log.error("转换MetricData失败: {}", metric, e);
            return null;
        }
    }

    /**
     * 带重试的写入
     */
    private int writeWithRetry(List<Point> points) {
        InfluxDBConfig.RetryOptions retryOptions = config.getRetryOptions();
        int maxRetries = retryOptions.getMaxRetries();
        long initialInterval = retryOptions.getInitialInterval();
        long maxInterval = retryOptions.getMaxInterval();
        double multiplier = retryOptions.getMultiplier();

        int attempt = 0;
        long currentInterval = initialInterval;

        while (attempt <= maxRetries) {
            try {
                influxDBClient.getWriteApiBlocking().writePoints(
                    config.getBucket(),
                    config.getOrg(),
                    points
                );
                return points.size();

            } catch (Exception e) {
                attempt++;
                if (attempt > maxRetries) {
                    log.error("写入失败，已达到最大重试次数: {}", maxRetries, e);
                    throw new TimeSeriesException("写入失败，已达到最大重试次数", e);
                }

                log.warn("写入失败，{} ms后进行第{}次重试",
                    currentInterval, attempt, e);

                try {
                    Thread.sleep(currentInterval);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TimeSeriesException("写入被中断", ie);
                }

                currentInterval = Math.min((long) (currentInterval * multiplier), maxInterval);
            }
        }

        return 0;
    }

    /**
     * 构建Flux查询语句
     */
    private String buildQuery(String probeKey, String metricName,
                             LocalDateTime start, LocalDateTime end,
                             String aggregateType, String window, String groupByTag) {
        StringBuilder query = new StringBuilder();

        // 基础查询
        query.append(String.format(
            "from(bucket: \"%s\") |> range(start: %s, stop: %s) ",
            config.getBucket(),
            start.format(DateTimeFormatter.ISO_DATE_TIME),
            end.format(DateTimeFormatter.ISO_DATE_TIME)
        ));

        // 过滤measurement和probe_key
        query.append(String.format(
            "|> filter(fn: (r) => r._measurement == \"%s\") ",
            metricName
        ));
        query.append(String.format(
            "|> filter(fn: (r) => r.probe_key == \"%s\") ",
            probeKey
        ));

        // 聚合
        if (aggregateType != null && window != null) {
            query.append(String.format(
                "|> aggregateWindow(every: %s, fn: %s) ",
                window, aggregateType
            ));
        }

        // 分组
        if (groupByTag != null) {
            query.append(String.format(
                "|> group(columns: [\"%s\"]) ",
                groupByTag
            ));
        }

        return query.toString();
    }

    /**
     * 执行查询并转换结果
     */
    private List<MetricData> executeQuery(String query) {
        List<FluxTable> tables = influxDBClient.getQueryApi().query(query, config.getOrg());
        List<MetricData> result = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                MetricData metric = convertRecordToMetricData(record);
                if (metric != null) {
                    result.add(metric);
                }
            }
        }

        return result;
    }

    /**
     * 转换FluxRecord为MetricData
     */
    private MetricData convertRecordToMetricData(FluxRecord record) {
        try {
            MetricData metric = new MetricData();

            // 基本信息
            Object probeKeyValue = record.getValueByKey("probe_key");
            metric.setProbeKey(probeKeyValue != null ? probeKeyValue.toString() : "");
            metric.setMetricName(record.getMeasurement());

            // 时间戳
            Instant instant = record.getTime();
            if (instant != null) {
                metric.setTimestamp(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
            }

            // 值
            Object value = record.getValue();
            if (value != null) {
                metric.setValue(((Number) value).doubleValue());
            }

            // 标签
            Map<String, String> tags = new HashMap<>();
            record.getValues().forEach((k, v) -> {
                if (!k.startsWith("_") && v != null) {
                    tags.put(k, v.toString());
                }
            });
            metric.setTags(tags);

            return metric;

        } catch (Exception e) {
            log.error("转换FluxRecord失败: {}", record, e);
            return null;
        }
    }
}
