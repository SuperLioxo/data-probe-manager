package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.WebhookConfig;
import com.lixin.probe.entity.WebhookEvent;
import com.lixin.probe.mapper.WebhookConfigMapper;
import com.lixin.probe.mapper.WebhookEventMapper;
import com.lixin.probe.service.WebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class WebhookServiceImpl implements WebhookService {

    @Autowired
    private WebhookConfigMapper webhookConfigMapper;

    @Autowired
    private WebhookEventMapper webhookEventMapper;

    @Override
    public Page<WebhookConfig> getWebhookConfigs(int pageNum, int pageSize) {
        return webhookConfigMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<WebhookConfig>().orderByDesc(WebhookConfig::getCreateTime));
    }

    @Override
    public WebhookConfig createWebhookConfig(WebhookConfig config) {
        config.setWebhookKey(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        config.setCreateTime(LocalDateTime.now());
        config.setEnabled(true);
        config.setReceiveCount(0L);
        webhookConfigMapper.insert(config);
        return config;
    }

    @Override
    public void deleteWebhookConfig(Long id) {
        webhookConfigMapper.deleteById(id);
    }

    @Override
    public String receiveWebhook(String webhookKey, String sourceIp, String payload) {
        WebhookConfig config = webhookConfigMapper.selectOne(
                new LambdaQueryWrapper<WebhookConfig>().eq(WebhookConfig::getWebhookKey, webhookKey));
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return "NOT_FOUND";
        }

        String checksum = md5(payload);
        WebhookEvent event = WebhookEvent.builder()
                .webhookKey(webhookKey)
                .sourceIp(sourceIp)
                .payload(payload)
                .payloadChecksum(checksum)
                .status("RECEIVED")
                .receivedTime(LocalDateTime.now())
                .build();
        webhookEventMapper.insert(event);

        webhookConfigMapper.update(null, new LambdaUpdateWrapper<WebhookConfig>()
                .eq(WebhookConfig::getId, config.getId())
                .set(WebhookConfig::getLastReceivedTime, LocalDateTime.now())
                .set(WebhookConfig::getReceiveCount, config.getReceiveCount() + 1));

        // Async processing placeholder
        try {
            event.setStatus("PROCESSED");
            event.setProcessedTime(LocalDateTime.now());
            event.setProcessResult("{\"status\":\"accepted\",\"targetProbe\":\"" + config.getTargetProbeKey() + "\"}");
            webhookEventMapper.updateById(event);
        } catch (Exception e) {
            log.warn("Webhook processing failed for key={}: {}", webhookKey, e.getMessage());
        }

        return "OK";
    }

    @Override
    public Page<WebhookEvent> getWebhookEvents(String webhookKey, int pageNum, int pageSize) {
        LambdaQueryWrapper<WebhookEvent> wrapper = new LambdaQueryWrapper<WebhookEvent>()
                .eq(webhookKey != null && !webhookKey.isEmpty(), WebhookEvent::getWebhookKey, webhookKey)
                .orderByDesc(WebhookEvent::getReceivedTime);
        return webhookEventMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Map<String, Object> getWebhookStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long configCount = webhookConfigMapper.selectCount(null);
        long enabledCount = webhookConfigMapper.selectCount(
                new LambdaQueryWrapper<WebhookConfig>().eq(WebhookConfig::getEnabled, true));
        long eventCount = webhookEventMapper.selectCount(null);
        stats.put("configCount", configCount);
        stats.put("enabledCount", enabledCount);
        stats.put("totalEvents", eventCount);
        return stats;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
