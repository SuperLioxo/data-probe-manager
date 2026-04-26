package com.lixin.probe.service.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixin.probe.entity.AlertChannel;
import com.lixin.probe.entity.ChangeAlertRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class WeComNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(WeComNotifier.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public boolean supports(String channelType) {
        return "WECOM".equalsIgnoreCase(channelType);
    }

    @Override
    public void send(AlertChannel channel, ChangeAlertRecord alert) {
        try {
            Map<String, Object> config = mapper.readValue(channel.getConfig(), Map.class);
            String webhookUrl = (String) config.get("webhookUrl");
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                log.warn("[WeCom] No webhookUrl configured for channel {}", channel.getName());
                return;
            }

            String body = mapper.writeValueAsString(Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of(
                            "content", String.format("### %s 告警\n> 探针: %s\n> 表: %s\n> 类型: %s\n> 级别: %s",
                                    alert.getAlertLevel(), alert.getProbeKey(), alert.getTableName(),
                                    alert.getChangeType(), alert.getAlertLevel()))
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[WeCom] Notification sent: status={}", response.statusCode());
        } catch (Exception e) {
            log.error("[WeCom] Failed to send notification: {}", e.getMessage());
        }
    }
}
