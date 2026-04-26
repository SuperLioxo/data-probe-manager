package com.lixin.probe.controller;

import com.lixin.probe.annotation.RequirePermission;
import com.lixin.probe.common.Permissions;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.MetricData;
import com.lixin.probe.service.MetricDataService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ValidationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统资源Controller
 * 提供系统资源查询的兼容接口
 */
@RestController
@RequestMapping("/api")
public class SystemResourcesController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SystemResourcesController.class);

    @Autowired
    private MetricDataService metricDataService;

    /**
     * 获取系统资源（兼容接口）
     * 前端调用 /api/system-resources/{id}
     *
     * @param probeId 探针ID
     * @return 最新指标数据列表
     */
    @GetMapping("/system-resources/{probeId}")
    @RequirePermission(Permissions.METRIC_VIEW)
    public Result<List<MetricData>> getSystemResources(@PathVariable Long probeId) {
        Result<Void> error = ValidationUtil.validateId(probeId, "探针ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(() -> {
            List<MetricData> data = metricDataService.getLatestMetrics(probeId);
            return data;
        }, "获取系统资源失败");
    }
}
