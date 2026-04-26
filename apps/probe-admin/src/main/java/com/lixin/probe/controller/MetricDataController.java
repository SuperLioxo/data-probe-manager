package com.lixin.probe.controller;

import com.lixin.probe.annotation.RateLimit;
import com.lixin.probe.common.Result;
import com.lixin.probe.dto.ProbeMetricsSummary;
import com.lixin.probe.entity.MetricData;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.MetricDataService;
import com.lixin.probe.service.ProbeService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ValidationUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控数据Controller
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricDataController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MetricDataController.class);

    @Autowired
    private MetricDataService metricDataService;

    @Autowired
    private com.lixin.probe.service.ProbeStatusValidationService statusValidationService;

    @Autowired
    @Qualifier("decoratedProbeService")
    private ProbeService probeService;

    /**
     * 查询探针指标数据
     */
    @GetMapping("/probe/{probeId}")
    public Result<List<MetricData>> getProbeMetrics(
            @PathVariable Long probeId,
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        Result<Void> error = ValidationUtil.validateId(probeId, "探针ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        // 验证探针是否在线
        Probe probe = probeService.getById(probeId);
        if (probe == null) {
            return Result.error("探针不存在");
        }
        if (!statusValidationService.isProbeOnline(probe.getProbeKey())) {
            log.warn("拒绝离线探针的指标数据查询: probeId={}, probeKey={}", probeId, probe.getProbeKey());
            return Result.error("探针离线，无法查询数据");
        }

        return ControllerHelper.safeGet(() -> {
            List<MetricData> data = metricDataService.getProbeMetrics(
                probeId, metricName, startTime, endTime);
            return data;
        }, "查询探针指标数据失败");
    }

    /**
     * 获取探针最新指标数据
     */
    @GetMapping("/probe/{probeId}/latest")
    public Result<List<MetricData>> getLatestMetrics(@PathVariable Long probeId) {
        Result<Void> error = ValidationUtil.validateId(probeId, "探针ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        // 验证探针是否在线
        Probe probe = probeService.getById(probeId);
        if (probe == null) {
            return Result.error("探针不存在");
        }
        if (!statusValidationService.isProbeOnline(probe.getProbeKey())) {
            log.warn("拒绝离线探针的最新指标查询: probeId={}, probeKey={}", probeId, probe.getProbeKey());
            return Result.error("探针离线，无法查询数据");
        }

        return ControllerHelper.safeGet(() -> {
            List<MetricData> data = metricDataService.getLatestMetrics(probeId);
            return data;
        }, "获取最新指标数据失败");
    }

    /**
     * 获取探针指标摘要（专用接口）
     * 返回前端显示所需的核心指标：CPU使用率、内存使用率、已用内存等
     *
     * @param probeId 探针ID
     * @return 指标摘要对象
     */
    @GetMapping("/probe/{probeId}/summary")
    public Result<ProbeMetricsSummary> getProbeMetricsSummary(@PathVariable Long probeId) {
        log.info("收到探针指标摘要请求: probeId={}", probeId);

        Result<Void> error = ValidationUtil.validateId(probeId, "探针ID");
        if (error != null) {
            log.warn("探针ID验证失败: probeId={}", probeId);
            return Result.error(error.getMessage());
        }

        // 验证探针是否在线
        Probe probe = probeService.getById(probeId);
        if (probe == null) {
            log.warn("探针不存在: probeId={}", probeId);
            return Result.error("探针不存在");
        }
        if (!statusValidationService.isProbeOnline(probe.getProbeKey())) {
            log.warn("拒绝离线探针的指标摘要查询: probeId={}, probeKey={}", probeId, probe.getProbeKey());
            return Result.error("探针离线，无法查询数据");
        }

        log.info("开始获取探针指标摘要: probeId={}, probeKey={}", probeId, probe.getProbeKey());
        return ControllerHelper.safeGet(() -> {
            ProbeMetricsSummary summary = metricDataService.getMetricsSummary(probeId);
            log.info("获取探针指标摘要成功: probeId={}, summary={}", probeId, summary);
            return summary;
        }, "获取探针指标摘要失败");
    }

    /**
     * 上报监控数据（供探针使用）
     */
    @RateLimit(capacity = 1000, refillRate = 100, key = "metric-report")
    @PostMapping
    public Result<String> report(@Valid @RequestBody MetricData metricData) {
        // 验证探针是否在线
        if (!statusValidationService.isProbeOnline(metricData.getProbeKey())) {
            log.warn("拒绝离线探针的指标数据上报: probeKey={}", metricData.getProbeKey());
            return Result.error("探针离线，拒绝接收数据: " + metricData.getProbeKey());
        }

        return ControllerHelper.safeExecute(
                () -> metricDataService.save(metricData),
                "数据上报成功",
                "上报监控数据失败"
        );
    }

    /**
     * 批量上报监控数据
     */
    @RateLimit(capacity = 100, refillRate = 10, key = "metric-batch-report")
    @PostMapping("/batch")
    public Result<String> batchReport(@Valid @RequestBody List<MetricData> metrics) {
        Result<Void> error = ValidationUtil.validateCollectionSize(metrics, "数据列表", 1000);
        if (error != null) {
            return Result.error(error.getMessage());
        }

        // 验证所有探针是否在线（批量中的第一个探针作为代表）
        if (!metrics.isEmpty()) {
            String probeKey = metrics.get(0).getProbeKey();
            if (!statusValidationService.isProbeOnline(probeKey)) {
                log.warn("拒绝离线探针的批量指标数据上报: probeKey={}", probeKey);
                return Result.error("探针离线，拒绝接收数据: " + probeKey);
            }
        }

        return ControllerHelper.safeExecute(
                () -> metricDataService.batchSave(metrics),
                "批量上报成功",
                "批量上报监控数据失败"
        );
    }

}
