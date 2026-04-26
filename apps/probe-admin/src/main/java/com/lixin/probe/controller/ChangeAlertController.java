package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.ChangeAlertConfig;
import com.lixin.probe.entity.ChangeAlertRecord;
import com.lixin.probe.service.ChangeAlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/change-alerts")
public class ChangeAlertController {

    @Autowired
    private ChangeAlertService changeAlertService;

    @GetMapping("/configs")
    public Result<Page<ChangeAlertConfig>> getConfigs(
            @RequestParam(required = false) String probeKey,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return Result.success(changeAlertService.getAlertConfigs(probeKey, pageNum, pageSize));
        } catch (Exception e) {
            return Result.error("查询告警配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/configs")
    public Result<ChangeAlertConfig> createConfig(@RequestBody ChangeAlertConfig config) {
        try {
            return Result.success(changeAlertService.createAlertConfig(config));
        } catch (Exception e) {
            return Result.error("创建告警配置失败: " + e.getMessage());
        }
    }

    @PutMapping("/configs/{id}")
    public Result<Void> updateConfig(@PathVariable Long id, @RequestBody ChangeAlertConfig config) {
        try {
            config.setId(id);
            changeAlertService.updateAlertConfig(config);
            return Result.success();
        } catch (Exception e) {
            return Result.error("更新告警配置失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/configs/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        try {
            changeAlertService.deleteAlertConfig(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("删除告警配置失败: " + e.getMessage());
        }
    }

    @GetMapping("/records")
    public Result<Page<ChangeAlertRecord>> getRecords(
            @RequestParam(required = false) String probeKey,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return Result.success(changeAlertService.getAlertRecords(probeKey, status, pageNum, pageSize));
        } catch (Exception e) {
            return Result.error("查询告警记录失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        try {
            return Result.success(changeAlertService.getAlertStatistics());
        } catch (Exception e) {
            return Result.error("查询统计失败: " + e.getMessage());
        }
    }
}
