package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.DataSourceAlertConfig;
import com.lixin.probe.entity.DataSourceAlertRecord;
import com.lixin.probe.service.DataSourceAlertService;
import com.lixin.probe.util.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/datasource-alerts")
public class DataSourceAlertController {

    @Autowired
    private DataSourceAlertService dataSourceAlertService;

    @GetMapping("/configs")
    public Result<Page<DataSourceAlertConfig>> listConfigs(
            @RequestParam(required = false) String probeKey,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ControllerHelper.safeGet(
                () -> dataSourceAlertService.getAlertConfigs(probeKey, pageNum, pageSize),
                "查询告警配置失败");
    }

    @PostMapping("/configs")
    public Result<String> createConfig(@RequestBody DataSourceAlertConfig config) {
        dataSourceAlertService.createAlertConfig(config);
        return Result.success("创建成功");
    }

    @PutMapping("/configs/{id}")
    public Result<String> updateConfig(@PathVariable Long id, @RequestBody DataSourceAlertConfig config) {
        return ControllerHelper.safeExecute(() -> {
            config.setId(id);
            dataSourceAlertService.updateAlertConfig(config);
        }, "更新成功", "更新告警配置失败");
    }

    @DeleteMapping("/configs/{id}")
    public Result<String> deleteConfig(@PathVariable Long id) {
        return ControllerHelper.safeExecute(
                () -> dataSourceAlertService.deleteAlertConfig(id),
                "删除成功", "删除告警配置失败");
    }

    @GetMapping("/records")
    public Result<Page<DataSourceAlertRecord>> listRecords(
            @RequestParam(required = false) String probeKey,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ControllerHelper.safeGet(
                () -> dataSourceAlertService.getAlertRecords(probeKey, status, pageNum, pageSize),
                "查询告警记录失败");
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return ControllerHelper.safeGet(
                () -> dataSourceAlertService.getAlertStatistics(),
                "查询告警统计失败");
    }
}
