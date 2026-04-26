package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.service.AggregationService;
import com.lixin.probe.util.ControllerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 汇聚数据查询 Controller
 * 前端从此接口读取汇聚后的数据，不直接访问原始数据源
 */
@RestController
@RequestMapping("/api/aggregation")
public class AggregationController {

    private static final Logger log = LoggerFactory.getLogger(AggregationController.class);

    @Autowired
    private AggregationService aggregationService;

    @GetMapping("/datasources")
    public Result<List<Map<String, Object>>> getDatasources() {
        return ControllerHelper.safeGet(() ->
                aggregationService.getAggregatedDataSources(), "获取汇聚数据源列表失败");
    }

    @GetMapping("/tables")
    public Result<List<Map<String, Object>>> getTables(
            @RequestParam(required = false) String sourceId) {
        return ControllerHelper.safeGet(() ->
                aggregationService.getAggregatedTables(sourceId), "获取汇聚表列表失败");
    }

    @GetMapping("/tables/{sourceId}/{tableName}/data")
    public Result<Map<String, Object>> getTableData(
            @PathVariable String sourceId,
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ControllerHelper.safeGet(() ->
                aggregationService.queryAggregatedData(sourceId, tableName, pageNum, pageSize),
                "查询汇聚数据失败");
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return ControllerHelper.safeGet(() ->
                aggregationService.getAggregationStats(), "获取汇聚统计失败");
    }
}
