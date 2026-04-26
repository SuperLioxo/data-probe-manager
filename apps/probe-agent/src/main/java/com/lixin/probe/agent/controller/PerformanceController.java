package com.lixin.probe.agent.controller;

import com.lixin.probe.agent.aspect.PerformanceMonitorAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 性能监控控制器
 * 提供方法执行统计、慢方法查询等性能监控接口
 *
 * @author probe-agent
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private static final Logger log = LoggerFactory.getLogger(PerformanceController.class);
    /**
     * 获取方法执行统计
     * GET /api/performance/statistics
     */
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "性能统计 / Performance statistics");

        Map<String, Object> statistics = PerformanceMonitorAspect.getStatistics();
        result.put("data", statistics);
        result.put("methodCount", statistics.size());

        return result;
    }

    /**
     * 获取慢方法列表（执行时间超过1秒）
     * GET /api/performance/slow-methods
     */
    @GetMapping("/slow-methods")
    public Map<String, Object> getSlowMethods() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "慢方法列表 / Slow methods (execution time > 1000ms)");

        Map<String, Object> allStats = PerformanceMonitorAspect.getStatistics();
        Map<String, Object> slowMethods = new LinkedHashMap<>();

        // 筛选平均执行时间超过1秒的方法
        allStats.forEach((method, stats) -> {
            Map<String, Object> methodStats = (Map<String, Object>) stats;
            long avgDuration = (Long) methodStats.get("avgDuration");

            if (avgDuration > 1000) {
                slowMethods.put(method, stats);
            }
        });

        result.put("data", slowMethods);
        result.put("count", slowMethods.size());

        return result;
    }

    /**
     * 获取最耗时方法（按平均耗时排序）
     * GET /api/performance/top-slow?limit=10
     */
    @GetMapping("/top-slow")
    public Map<String, Object> getTopSlowMethods(Integer limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "最耗时方法 / Top slow methods by average execution time");

        if (limit == null || limit <= 0) {
            limit = 10;
        }

        Map<String, Object> allStats = PerformanceMonitorAspect.getStatistics();
        Map<String, Long> avgDurations = new LinkedHashMap<>();

        // 提取平均耗时
        allStats.forEach((method, stats) -> {
            Map<String, Object> methodStats = (Map<String, Object>) stats;
            long avgDuration = (Long) methodStats.get("avgDuration");
            avgDurations.put(method, avgDuration);
        });

        // 按平均耗时排序
        Map<String, Object> sortedMethods = new LinkedHashMap<>();
        avgDurations.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .forEach(entry -> sortedMethods.put(entry.getKey(), allStats.get(entry.getKey())));

        result.put("data", sortedMethods);
        result.put("limit", limit);
        result.put("count", sortedMethods.size());

        return result;
    }

    /**
     * 重置性能统计
     * POST /api/performance/reset
     */
    @PostMapping("/reset")
    public Map<String, Object> resetStatistics() {
        PerformanceMonitorAspect.resetStatistics();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "性能统计已重置 / Performance statistics reset");
        result.put("timestamp", System.currentTimeMillis());

        log.info("性能统计已重置");

        return result;
    }

    /**
     * 获取性能概览
     * GET /api/performance/overview
     */
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "性能概览 / Performance overview");

        Map<String, Object> allStats = PerformanceMonitorAspect.getStatistics();

        long totalCalls = 0;
        long totalSuccess = 0;
        long totalFailure = 0;
        long totalExecutionTime = 0;
        int slowMethodCount = 0;

        for (Map.Entry<String, Object> entry : allStats.entrySet()) {
            Map<String, Object> stats = (Map<String, Object>) entry.getValue();
            totalCalls += (Long) stats.get("totalCount");
            totalSuccess += (Long) stats.get("successCount");
            totalFailure += (Long) stats.get("failureCount");
            totalExecutionTime += (Long) stats.get("totalTime");

            if ((Long) stats.get("avgDuration") > 1000) {
                slowMethodCount++;
            }
        }

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalCalls", totalCalls);
        overview.put("totalSuccess", totalSuccess);
        overview.put("totalFailure", totalFailure);
        overview.put("successRate", totalCalls > 0 ? String.format("%.2f%%", (double) totalSuccess / totalCalls * 100) : "N/A");
        overview.put("totalExecutionTime", totalExecutionTime);
        overview.put("avgExecutionTime", totalCalls > 0 ? totalExecutionTime / totalCalls : 0);
        overview.put("monitoredMethods", allStats.size());
        overview.put("slowMethodCount", slowMethodCount);

        result.put("data", overview);

        return result;
    }
}
