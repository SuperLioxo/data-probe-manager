package com.lixin.probe.controller;

import com.lixin.probe.annotation.RequirePermission;
import com.lixin.probe.common.Permissions;
import com.lixin.probe.common.Result;
import com.lixin.probe.dto.MetricData;
import com.lixin.probe.timeseries.TimeSeriesException;
import com.lixin.probe.timeseries.impl.InfluxDBAdapterEnhanced;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 时序数据查询控制器
 *
 * <p>提供InfluxDB时序数据的查询和写入API。</p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@RestController
@RequestMapping("/api/timeseries")
@Tag(name = "时序数据管理", description = "InfluxDB时序数据查询和写入API")
@ConditionalOnBean(InfluxDBAdapterEnhanced.class)
public class MetricQueryController {

    private static final Logger log = LoggerFactory.getLogger(MetricQueryController.class);

    @Autowired
    private InfluxDBAdapterEnhanced influxDBAdapter;

    /**
     * 写入指标数据
     *
     * @param metrics 指标数据列表
     * @return 写入结果
     */
    @PostMapping("/write")
    @Operation(summary = "写入指标数据", description = "批量写入指标数据到InfluxDB")
    @RequirePermission(Permissions.METRIC_EXPORT)
    public Result<String> writeMetrics(@RequestBody List<MetricData> metrics) {
        try {
            if (metrics == null || metrics.isEmpty()) {
                return Result.error("指标数据不能为空");
            }

            String probeKey = metrics.get(0).getProbeKey();
            influxDBAdapter.writeMetrics(probeKey, metrics);

            log.info("成功写入{}条指标数据", metrics.size());
            return Result.success("成功写入" + metrics.size() + "条数据");

        } catch (TimeSeriesException e) {
            log.error("写入指标数据失败", e);
            return Result.error("写入失败: " + e.getMessage());
        }
    }

    /**
     * 异步写入指标数据
     *
     * @param metrics 指标数据列表
     * @return CompletableFuture
     */
    @PostMapping("/write-async")
    @Operation(summary = "异步写入指标数据", description = "异步批量写入指标数据到InfluxDB")
    @RequirePermission(Permissions.METRIC_EXPORT)
    public CompletableFuture<Result<String>> writeMetricsAsync(@RequestBody List<MetricData> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return CompletableFuture.completedFuture(Result.error("指标数据不能为空"));
        }

        String probeKey = metrics.get(0).getProbeKey();

        CompletableFuture<Result<String>> result = new CompletableFuture<>();
        influxDBAdapter.writeMetricsAsync(probeKey, metrics)
            .thenAccept(count -> {
                log.info("异步成功写入{}条指标数据", count);
                result.complete(Result.<String>success("成功写入" + count + "条数据"));
            })
            .exceptionally(e -> {
                log.error("异步写入指标数据失败", e);
                result.complete(Result.<String>error("写入失败: " + e.getMessage()));
                return null;
            });
        return result;
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
    @GetMapping("/query")
    @Operation(summary = "查询原始数据", description = "按时间范围查询原始指标数据")
    @RequirePermission(Permissions.METRIC_VIEW)
    public Result<List<MetricData>> queryMetrics(
        @Parameter(description = "探针标识") @RequestParam String probeKey,
        @Parameter(description = "指标名称") @RequestParam String metricName,
        @Parameter(description = "开始时间") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
        @Parameter(description = "结束时间") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        try {
            List<MetricData> metrics = influxDBAdapter.queryMetrics(
                probeKey, metricName, start, end
            );

            log.info("查询到{}条指标数据", metrics.size());
            return Result.success(metrics);

        } catch (TimeSeriesException e) {
            log.error("查询指标数据失败", e);
            return Result.error("查询失败: " + e.getMessage());
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
    @GetMapping("/query/latest")
    @Operation(summary = "查询最新数据", description = "查询最新的N条指标数据")
    @RequirePermission(Permissions.METRIC_VIEW)
    public Result<List<MetricData>> queryLatestMetrics(
        @Parameter(description = "探针标识") @RequestParam String probeKey,
        @Parameter(description = "指标名称") @RequestParam String metricName,
        @Parameter(description = "返回记录数") @RequestParam(defaultValue = "10") int limit
    ) {
        try {
            List<MetricData> metrics = influxDBAdapter.queryLatestMetrics(
                probeKey, metricName, limit
            );

            log.info("查询到{}条最新数据", metrics.size());
            return Result.success(metrics);

        } catch (TimeSeriesException e) {
            log.error("查询最新数据失败", e);
            return Result.error("查询失败: " + e.getMessage());
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
     * @param window 时间窗口（1m, 5m, 1h, 1d）
     * @return 聚合后的指标数据列表
     */
    @GetMapping("/query/aggregate")
    @Operation(summary = "聚合查询", description = "按时间窗口聚合查询指标数据")
    @RequirePermission(Permissions.METRIC_VIEW)
    public Result<List<MetricData>> queryAggregate(
        @Parameter(description = "探针标识") @RequestParam String probeKey,
        @Parameter(description = "指标名称") @RequestParam String metricName,
        @Parameter(description = "开始时间") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
        @Parameter(description = "结束时间") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
        @Parameter(description = "聚合类型（mean, max, min, sum, count）") @RequestParam String aggregateType,
        @Parameter(description = "时间窗口（1m, 5m, 1h, 1d）") @RequestParam String window
    ) {
        try {
            List<MetricData> metrics = influxDBAdapter.queryAggregate(
                probeKey, metricName, start, end, aggregateType, window
            );

            log.info("聚合查询到{}条数据", metrics.size());
            return Result.success(metrics);

        } catch (TimeSeriesException e) {
            log.error("聚合查询失败", e);
            return Result.error("聚合查询失败: " + e.getMessage());
        }
    }

    /**
     * 分组聚合查询
     *
     * @param probeKey 探针标识
     * @param metricName 指标名称
     * @param start 开始时间
     * @param end 结束时间
     * @param groupByTag 分组标签
     * @param aggregateType 聚合类型
     * @param window 时间窗口
     * @return 分组聚合后的指标数据列表
     */
    @GetMapping("/query/group-by")
    @Operation(summary = "分组聚合查询", description = "按标签分组并聚合查询指标数据")
    @RequirePermission(Permissions.METRIC_VIEW)
    public Result<List<MetricData>> queryGroupBy(
        @Parameter(description = "探针标识") @RequestParam String probeKey,
        @Parameter(description = "指标名称") @RequestParam String metricName,
        @Parameter(description = "开始时间") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
        @Parameter(description = "结束时间") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
        @Parameter(description = "分组标签") @RequestParam String groupByTag,
        @Parameter(description = "聚合类型（mean, max, min, sum, count）") @RequestParam String aggregateType,
        @Parameter(description = "时间窗口（1m, 5m, 1h, 1d）") @RequestParam String window
    ) {
        try {
            List<MetricData> metrics = influxDBAdapter.queryGroupBy(
                probeKey, metricName, start, end,
                groupByTag, aggregateType, window
            );

            log.info("分组聚合查询到{}条数据", metrics.size());
            return Result.success(metrics);

        } catch (TimeSeriesException e) {
            log.error("分组聚合查询失败", e);
            return Result.error("分组聚合查询失败: " + e.getMessage());
        }
    }

    /**
     * 删除旧数据
     *
     * @param probeKey 探针标识
     * @param before 删除此时间之前的数据
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除旧数据", description = "删除指定时间之前的旧数据")
    @RequirePermission(Permissions.SYSTEM_ADMIN)
    public Result<String> deleteOldData(
        @Parameter(description = "探针标识") @RequestParam String probeKey,
        @Parameter(description = "删除此时间之前的数据") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before
    ) {
        try {
            influxDBAdapter.deleteOldData(probeKey, before);

            log.info("成功删除探{}在{}之前的旧数据", probeKey, before);
            return Result.success("删除成功");

        } catch (TimeSeriesException e) {
            log.error("删除旧数据失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查InfluxDB连接状态")
    public Result<Boolean> healthCheck() {
        boolean healthy = influxDBAdapter.healthCheck();

        if (healthy) {
            return Result.success("InfluxDB连接正常", true);
        } else {
            return Result.error("InfluxDB连接异常");
        }
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取统计信息", description = "获取InfluxDB统计信息")
    @RequirePermission(Permissions.SYSTEM_ADMIN)
    public Result<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = influxDBAdapter.getStatistics();
        return Result.success(stats);
    }
}
