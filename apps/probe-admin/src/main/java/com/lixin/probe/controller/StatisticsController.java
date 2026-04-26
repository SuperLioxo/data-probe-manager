package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.service.StatisticsService;
import com.lixin.probe.util.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 统计数据Controller
 */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StatisticsController.class);

    @Autowired
    private StatisticsService statisticsService;

    /**
     * 获取探针统计
     */
    @GetMapping("/probes")
    public Result<Map<String, Object>> getProbeStats() {
        return ControllerHelper.safeGet(
                statisticsService::getProbeStatistics,
                "获取探针统计失败"
        );
    }

    /**
     * 获取概览统计
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(
            @RequestParam(defaultValue = "24h") String timeRange) {
        log.info("获取概览统计, timeRange={}", timeRange);
        return ControllerHelper.safeGet(
                () -> statisticsService.getOverview(timeRange),
                "获取概览统计失败"
        );
    }

    /**
     * 获取探针趋势数据
     */
    @GetMapping("/probe-trend")
    public Result<Map<String, Object>> getProbeTrend(
            @RequestParam(defaultValue = "24h") String timeRange,
            @RequestParam(defaultValue = "cpu") String metric) {
        log.info("获取探针趋势数据, timeRange={}, metric={}", timeRange, metric);

        return ControllerHelper.safeGet(
                () -> statisticsService.getProbeTrend(timeRange, metric),
                "获取探针趋势数据失败"
        );
    }

    /**
     * 获取指标分布统计
     */
    @GetMapping("/metric-distribution")
    public Result<Map<String, Object>> getMetricDistribution(
            @RequestParam(defaultValue = "24h") String timeRange) {
        log.info("获取指标分布统计, timeRange={}", timeRange);

        return ControllerHelper.safeGet(
                () -> statisticsService.getMetricDistribution(timeRange),
                "获取指标分布统计失败"
        );
    }

    /**
     * 获取告警趋势统计
     */
    @GetMapping("/alert-trend")
    public Result<Map<String, Object>> getAlertTrend(
            @RequestParam(defaultValue = "7d") String timeRange) {
        log.info("获取告警趋势统计, timeRange={}", timeRange);

        return ControllerHelper.safeGet(
                () -> statisticsService.getAlertTrend(timeRange),
                "获取告警趋势统计失败"
        );
    }

    /**
     * 获取探针状态趋势统计
     */
    @GetMapping("/probe-status-trend")
    public Result<Map<String, Object>> getProbeStatusTrend(
            @RequestParam(defaultValue = "7d") String timeRange) {
        log.info("获取探针状态趋势统计, timeRange={}", timeRange);

        return ControllerHelper.safeGet(
                () -> statisticsService.getProbeStatusTrend(timeRange),
                "获取探针状态趋势统计失败"
        );
    }

    /**
     * 获取探针排行统计
     */
    @GetMapping("/probe-ranking")
    public Result<Map<String, Object>> getProbeRanking(
            @RequestParam(defaultValue = "24h") String timeRange,
            @RequestParam(defaultValue = "dataCount") String metric) {
        log.info("获取探针排行统计, timeRange={}, metric={}", timeRange, metric);

        return ControllerHelper.safeGet(
                () -> statisticsService.getProbeRanking(timeRange, metric),
                "获取探针排行统计失败"
        );
    }
}
