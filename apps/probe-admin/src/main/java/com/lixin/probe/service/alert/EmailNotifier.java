package com.lixin.probe.service.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixin.probe.entity.AlertChannel;
import com.lixin.probe.entity.ChangeAlertRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmailNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Override
    public boolean supports(String channelType) {
        return "EMAIL".equalsIgnoreCase(channelType);
    }

    @Override
    public void send(AlertChannel channel, ChangeAlertRecord alert) {
        if (mailSender == null) {
            log.warn("[Email] JavaMailSender not configured, skipping email notification");
            return;
        }
        try {
            Map<String, Object> config = mapper.readValue(channel.getConfig(), Map.class);
            String to = (String) config.get("to");
            String from = (String) config.getOrDefault("from", "probe-admin@localhost");
            if (to == null || to.isEmpty()) {
                log.warn("[Email] No 'to' address configured for channel {}", channel.getName());
                return;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to.split(","));
            message.setSubject(String.format("[数据探针告警] %s - %s", alert.getAlertLevel(), alert.getChangeType()));
            message.setText(String.format(
                    "告警级别: %s\n探针: %s\n表: %s\n变化类型: %s\n变化详情: %s\n影响行数: %s\n时间: %s",
                    alert.getAlertLevel(), alert.getProbeKey(), alert.getTableName(),
                    alert.getChangeType(), alert.getChangeDetail(), alert.getAffectedRows(),
                    alert.getCreatedTime()));

            mailSender.send(message);
            log.info("[Email] Notification sent to {}", to);
        } catch (Exception e) {
            log.error("[Email] Failed to send notification: {}", e.getMessage());
        }
    }
}
