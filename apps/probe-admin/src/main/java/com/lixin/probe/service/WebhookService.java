package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.WebhookConfig;
import com.lixin.probe.entity.WebhookEvent;

import java.util.Map;

public interface WebhookService {

    Page<WebhookConfig> getWebhookConfigs(int pageNum, int pageSize);

    WebhookConfig createWebhookConfig(WebhookConfig config);

    void deleteWebhookConfig(Long id);

    String receiveWebhook(String webhookKey, String sourceIp, String payload);

    Page<WebhookEvent> getWebhookEvents(String webhookKey, int pageNum, int pageSize);

    Map<String, Object> getWebhookStatistics();
}
