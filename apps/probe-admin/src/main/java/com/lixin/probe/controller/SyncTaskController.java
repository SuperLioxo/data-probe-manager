package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.SyncLog;
import com.lixin.probe.entity.SyncTask;
import com.lixin.probe.service.SyncTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync-tasks")
public class SyncTaskController {

    @Autowired
    private SyncTaskService syncTaskService;

    @GetMapping
    public Result<Page<SyncTask>> list(
            @RequestParam(required = false) String probeKey,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return Result.success(syncTaskService.getTasks(probeKey, status, pageNum, pageSize));
        } catch (Exception e) {
            return Result.error("查询同步任务失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<SyncTask> getById(@PathVariable Long id) {
        try {
            return Result.success(syncTaskService.getTask(id));
        } catch (Exception e) {
            return Result.error("查询同步任务失败: " + e.getMessage());
        }
    }

    @PostMapping
    public Result<SyncTask> create(@RequestBody SyncTask task) {
        try {
            return Result.success(syncTaskService.createTask(task));
        } catch (Exception e) {
            return Result.error("创建同步任务失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<SyncTask> update(@PathVariable Long id, @RequestBody SyncTask task) {
        try {
            task.setId(id);
            return Result.success(syncTaskService.updateTask(task));
        } catch (Exception e) {
            return Result.error("更新同步任务失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            syncTaskService.deleteTask(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("删除同步任务失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        try {
            syncTaskService.toggleTask(id, enabled);
            return Result.success();
        } catch (Exception e) {
            return Result.error("切换任务状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/trigger")
    public Result<Void> trigger(@PathVariable Long id) {
        try {
            syncTaskService.triggerSync(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("触发同步失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/logs")
    public Result<Page<SyncLog>> logs(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return Result.success(syncTaskService.getSyncLogs(id, status, pageNum, pageSize));
        } catch (Exception e) {
            return Result.error("查询同步日志失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        try {
            return Result.success(syncTaskService.getSyncStatistics());
        } catch (Exception e) {
            return Result.error("查询统计失败: " + e.getMessage());
        }
    }
}
