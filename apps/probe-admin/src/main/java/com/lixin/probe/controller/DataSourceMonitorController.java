package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.DatasourceMonitorLog;
import com.lixin.probe.service.DataSourceMonitorEnhancedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/datasource-monitor")
public class DataSourceMonitorController {

    @Autowired
    private DataSourceMonitorEnhancedService monitorService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) String probeKey) {
        try {
            return Result.success(monitorService.getMetricsOverview(probeKey));
        } catch (Exception e) {
            return Result.error("查询监控概览失败: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public Result<Page<DatasourceMonitorLog>> history(
            @RequestParam(required = false) String probeKey,
            @RequestParam(required = false) String metricType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize) {
        try {
            return Result.success(monitorService.getMetricHistory(probeKey, metricType, pageNum, pageSize));
        } catch (Exception e) {
            return Result.error("查询历史数据失败: " + e.getMessage());
        }
    }
}
