package com.lixin.probe.service.impl;

import com.lixin.probe.entity.Alert;
import com.lixin.probe.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通知服务实现
 * 简单的日志通知实现
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void sendAlert(Alert alert) {
        log.warn("[ALERT] 告警通知 - 探针: {}, 严重程度: {}, 消息: {}",
                alert.getProbeName(), alert.getSeverity(), alert.getMessage());
        // TODO: 实现实际的通知方式（邮件、短信、钉钉等）
    }

    @Override
    public void sendAlertNotification(Alert alert) {
        log.warn("[ALERT] 告警通知 - 探针: {}, 严重程度: {}, 消息: {}",
                alert.getProbeName(), alert.getSeverity(), alert.getMessage());
        // TODO: 实现实际的通知方式（邮件、短信、钉钉等）
    }

    @Override
    public void sendAlertRecoveryNotification(Alert alert) {
        log.info("[RECOVERY] 告警恢复 - 探针: {}, 消息: {}",
                alert.getProbeName(), alert.getMessage());
        // TODO: 实现实际的通知方式
    }

    @Override
    public void sendAlertResolution(Long alertId, String resolution) {
        log.info("[RESOLUTION] 告警解决 - ID: {}, 解决方案: {}", alertId, resolution);
        // TODO: 实现实际的通知方式
    }
}
