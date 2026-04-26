package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.ChangeLog;
import com.lixin.probe.entity.DataSnapshot;
import com.lixin.probe.service.ChangeDetectionService;
import com.lixin.probe.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/change-detection")
public class ChangeDetectionController {

    @Autowired
    private ChangeDetectionService changeDetectionService;

    /**
     * 分页查询变化日志
     */
    @GetMapping("/logs")
    public Result<Page<ChangeLog>> getLogs(
            @RequestParam(required = false) String probeKey,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String changeType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            Page<ChangeLog> page = changeDetectionService.getChangeLogPage(
                    probeKey, tableName, changeType, pageNum, pageSize);
            return Result.success(page);
        } catch (Exception e) {
            return Result.error("查询变化日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取变化统计
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) String probeKey) {
        try {
            Map<String, Object> stats = changeDetectionService.getChangeStatistics(probeKey);
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error("查询变化统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近的变化记录
     */
    @GetMapping("/recent")
    public Result<List<ChangeLog>> getRecent(
            @RequestParam(required = false) String probeKey,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<ChangeLog> changes = changeDetectionService.getRecentChanges(probeKey, limit);
            return Result.success(changes);
        } catch (Exception e) {
            return Result.error("查询最近变化失败: " + e.getMessage());
        }
    }

    /**
     * 获取表的快照历史
     */
    @GetMapping("/snapshots")
    public Result<List<DataSnapshot>> getSnapshots(
            @RequestParam String probeKey,
            @RequestParam String tableName,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<DataSnapshot> snapshots = changeDetectionService.getLatestSnapshots(
                    probeKey, tableName, limit);
            return Result.success(snapshots);
        } catch (Exception e) {
            return Result.error("查询快照失败: " + e.getMessage());
        }
    }

    @PostMapping("/detect/{probeKey}")
    public Result<List<ChangeLog>> triggerDetection(@PathVariable String probeKey) {
        try {
            List<ChangeLog> changes = changeDetectionService.redetectFromLatestSnapshots(probeKey);
            return Result.success(changes);
        } catch (Exception e) {
            return Result.error("变化检测失败: " + e.getMessage());
        }
    }
}
