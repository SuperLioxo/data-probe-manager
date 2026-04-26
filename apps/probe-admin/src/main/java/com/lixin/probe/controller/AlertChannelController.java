package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.AlertChannel;
import com.lixin.probe.service.AlertNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alert-channels")
public class AlertChannelController {

    @Autowired
    private AlertNotificationService alertNotificationService;

    @GetMapping
    public Result<Page<AlertChannel>> list(
            @RequestParam(required = false) String channelType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(alertNotificationService.listChannels(channelType, pageNum, pageSize));
    }

    @PostMapping
    public Result<AlertChannel> create(@RequestBody AlertChannel channel) {
        return Result.success(alertNotificationService.createChannel(channel));
    }

    @PutMapping("/{id}")
    public Result<AlertChannel> update(@PathVariable Long id, @RequestBody AlertChannel channel) {
        channel.setId(id);
        return Result.success(alertNotificationService.updateChannel(channel));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        alertNotificationService.deleteChannel(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/test")
    public Result<Boolean> test(@PathVariable Long id) {
        return Result.success(alertNotificationService.testChannel(id));
    }
}
