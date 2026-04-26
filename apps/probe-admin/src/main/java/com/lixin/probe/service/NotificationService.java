package com.lixin.probe.service;

import com.lixin.probe.entity.Alert;

/**
 * 通知服务接口
 * 用于发送告警通知
 */
public interface NotificationService {

    /**
     * 发送告警通知
     *
     * @param alert 告警对象
     */
    void sendAlert(Alert alert);

    /**
     * 发送告警恢复通知
     *
     * @param alert 告警对象
     */
    void sendAlertNotification(Alert alert);

    /**
     * 发送告警恢复通知
     *
     * @param alert 告警对象
     */
    void sendAlertRecoveryNotification(Alert alert);

    /**
     * 发送告警解决通知
     *
     * @param alertId 告警ID
     * @param resolution 解决方案
     */
    void sendAlertResolution(Long alertId, String resolution);
}
