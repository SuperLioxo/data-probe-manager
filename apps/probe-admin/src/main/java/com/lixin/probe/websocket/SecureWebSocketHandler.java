package com.lixin.probe.websocket;

import com.lixin.probe.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;

/**
 * WebSocket 安全增强基类
 * 提供连接安全验证和日志记录
 *
 * @author Claude Code
 * @version 2.0 - 安全增强版
 */
public abstract class SecureWebSocketHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 验证 WebSocket 连接的安全性
     *
     * @param session WebSocket Session
     * @return        是否允许连接
     */
    protected boolean validateConnectionSecurity(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            log.warn("WebSocket 连接无效：URI 为空");
            return false;
        }

        String query = uri.getQuery();
        if (query == null) {
            log.warn("WebSocket 连接无效：无查询参数");
            return false;
        }

        // 检查是否包含敏感信息
        if (query.contains("probe_key") || query.contains("password") || query.contains("token")) {
            log.error("安全警告：敏感信息通过 URL 参数传递，连接已拒绝。URI: {}", maskSensitiveData(uri.toString()));
            return false;
        }

        return true;
    }

    /**
     * 提取并记录连接信息
     *
     * @param session WebSocket Session
     * @return        格式化的连接信息
     */
    protected String extractConnectionInfo(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return "URI=null";
        }

        return String.format(
            "uri=%s, query=%s, remoteAddress=%s",
            uri.getPath(),
            maskSensitiveData(uri.getQuery()),
            session.getRemoteAddress()
        );
    }

    /**
     * 掩盖敏感数据（用于日志）
     *
     * @param data 原始数据
     * @return     掩盖后的数据
     */
    protected String maskSensitiveData(String data) {
        if (data == null) {
            return "null";
        }

        // 掩盖 probe_key 参数
        if (data.contains("probe_key")) {
            return data.replaceAll("probe_key=[^&]*", "probe_key=****");
        }

        // 掩盖 password 参数
        if (data.contains("password")) {
            return data.replaceAll("password=[^&]*", "password=****");
        }

        // 掩盖 token 参数
        if (data.contains("token")) {
            return data.replaceAll("token=[^&]*", "token=****");
        }

        return data;
    }

    /**
     * 安全地关闭连接
     *
     * @param session WebSocket Session
     * @param reason  关闭原因
     * @param status  关闭状态
     */
    protected void closeSessionSafely(WebSocketSession session, String reason, CloseStatus status) {
        try {
            if (session.isOpen()) {
                log.info("关闭 WebSocket 连接：sessionId={}, reason={}", session.getId(), reason);
                session.close(status);
            }
        } catch (Exception e) {
            log.error("关闭 WebSocket 连接失败：sessionId={}", session.getId(), e);
        }
    }

    /**
     * 验证消息完整性（使用 HMAC）
     *
     * @param payload    消息载荷
     * @param signature  签名
     * @param key        密钥
     * @return           是否验证通过
     */
    protected boolean verifyMessageIntegrity(String payload, String signature, String key) {
        try {
            return com.lixin.probe.util.CryptoUtil.verifyHMAC(payload, key, signature);
        } catch (Exception e) {
            log.error("消息完整性验证失败", e);
            return false;
        }
    }

    /**
     * 创建带签名的消息
     *
     * @param message 原始消息
     * @param key     密钥
     * @return        带签名的消息格式
     */
    protected String createSignedMessage(String message, String key) {
        try {
            String signature = com.lixin.probe.util.CryptoUtil.signHMAC(message, key);
            return String.format("{\"data\":\"%s\",\"signature\":\"%s\"}", message, signature);
        } catch (Exception e) {
            log.error("创建签名消息失败", e);
            return message;
        }
    }

    /**
     * 发送错误消息
     *
     * @param session WebSocket Session
     * @param error   错误信息
     */
    protected void sendErrorMessage(WebSocketSession session, String error) {
        try {
            if (session.isOpen()) {
                TextMessage errorMessage = new TextMessage(
                    String.format("{\"type\":\"ERROR\",\"message\":\"%s\"}", error)
                );
                session.sendMessage(errorMessage);
            }
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }
}
