package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.WebhookConfig;
import com.lixin.probe.entity.WebhookEvent;
import com.lixin.probe.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    @Autowired
    private WebhookService webhookService;

    @PostMapping("/receive/{webhookKey}")
    public Map<String, String> receive(
            @PathVariable String webhookKey,
            @RequestBody String payload,
            HttpServletRequest request) {
        String sourceIp = request.getRemoteAddr();
        String status = webhookService.receiveWebhook(webhookKey, sourceIp, payload);
        return Map.of("status", status, "timestamp", java.time.LocalDateTime.now().toString());
    }

    @GetMapping("/configs")
    public Result<Page<WebhookConfig>> getConfigs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return Result.success(webhookService.getWebhookConfigs(pageNum, pageSize));
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/configs")
    public Result<WebhookConfig> createConfig(@RequestBody WebhookConfig config) {
        try {
            return Result.success(webhookService.createWebhookConfig(config));
        } catch (Exception e) {
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/configs/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        try {
            webhookService.deleteWebhookConfig(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/events")
    public Result<Page<WebhookEvent>> getEvents(
            @RequestParam(required = false) String webhookKey,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return Result.success(webhookService.getWebhookEvents(webhookKey, pageNum, pageSize));
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        try {
            return Result.success(webhookService.getWebhookStatistics());
        } catch (Exception e) {
            return Result.error("查询统计失败: " + e.getMessage());
        }
    }
}
