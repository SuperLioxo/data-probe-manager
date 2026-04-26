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

@Component
public class WebhookNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public boolean supports(String channelType) {
        return "WEBHOOK".equalsIgnoreCase(channelType);
    }

    @Override
    public void send(AlertChannel channel, ChangeAlertRecord alert) {
        try {
            var config = mapper.readValue(channel.getConfig(), java.util.Map.class);
            String url = (String) config.get("url");
            String secret = (String) config.get("secret");
            if (url == null || url.isEmpty()) return;

            String body = mapper.writeValueAsString(alert);
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10));
            if (secret != null && !secret.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + secret);
            }

            HttpResponse<String> response = http.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            log.info("[Webhook] Notification sent to {}: status={}", url, response.statusCode());
        } catch (Exception e) {
            log.error("[Webhook] Failed to send notification: {}", e.getMessage());
        }
    }
}
