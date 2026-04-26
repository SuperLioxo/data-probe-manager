package com.lixin.probe.websocket;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.websocket.handler.MessageDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文件探针WebSocket处理器（重构版）
 * 职责：连接管理、会话超时清理、消息分发
 * 业务逻辑处理委托给MessageDispatcher
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (重构版)
 */
@Component
public class FileProbeWebSocketHandler extends TextWebSocketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileProbeWebSocketHandler.class);

    @Autowired
    private MessageDispatcher messageDispatcher;

    // 存储连接的会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 存储会话最后活动时间
    private final Map<String, Long> sessionLastActivity = new ConcurrentHashMap<>();

    // 会话超时时间（30分钟）
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;

    // 定时任务执行器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 构造函数，启动会话清理任务
    public FileProbeWebSocketHandler() {
        // 每5分钟执行一次会话清理
        scheduler.scheduleAtFixedRate(this::cleanupInactiveSessions, 5, 5, TimeUnit.MINUTES);
        log.info("WebSocket会话清理任务已启动，超时时间: {}ms", SESSION_TIMEOUT_MS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String probeKey = extractProbeKey(session);
        if (probeKey != null) {
            sessions.put(probeKey, session);
            sessionLastActivity.put(probeKey, System.currentTimeMillis());
            log.info("文件探针WebSocket连接建立: probeKey={}", probeKey);

            // 发送连接确认消息
            session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"message\":\"连接成功\"}"));
        } else {
            log.warn("WebSocket连接缺少probe_key参数");
            session.close();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到WebSocket消息: {}", payload);

        // 更新会话活动时间
        String probeKey = extractProbeKey(session);
        if (probeKey != null) {
            sessionLastActivity.put(probeKey, System.currentTimeMillis());
        }

        try {
            // 1. 解析JSON消息
            @SuppressWarnings("unchecked")
            Map<String, Object> json = JSON.parseObject(payload, new HashMap<String, Object>().getClass());
            String type = (String) json.get("type");

            // 2. 防御性检查：确保type不为null
            if (type == null) {
                log.warn("WebSocket消息缺少type字段: {}", payload);
                sendError(session, "消息格式错误：缺少type字段");
                return;
            }

            // 3. 委托给MessageDispatcher处理业务逻辑
            messageDispatcher.dispatch(session, probeKey, type, null, json.get("data"));

        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String probeKey = extractProbeKey(session);
        if (probeKey != null) {
            sessions.remove(probeKey);
            sessionLastActivity.remove(probeKey);
            log.info("文件探针WebSocket连接关闭: probeKey={}, status={}", probeKey, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误: session={}", session.getId(), exception);
    }

    // ========== 发送消息方法 ==========

    /**
     * 发送消息到指定探针
     */
    public boolean sendMessage(String probeKey, String message) {
        WebSocketSession session = sessions.get(probeKey);
        if (session == null || !session.isOpen()) {
            return false;
        }

        try {
            session.sendMessage(new TextMessage(message));
            return true;
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return false;
        }
    }

    /**
     * 发送扫描指令
     */
    public boolean sendScanCommand(String probeKey, String scanPath) {
        WebSocketSession session = sessions.get(probeKey);
        if (session == null) {
            log.warn("探针未连接: probeKey={}", probeKey);
            return false;
        }

        if (!session.isOpen()) {
            log.warn("探针连接已关闭: probeKey={}", probeKey);
            return false;
        }

        try {
            String command = String.format(
                "{\"type\":\"SCAN_COMMAND\",\"data\":{\"probeKey\":\"%s\",\"scanPath\":\"%s\"}}",
                probeKey, scanPath != null ? scanPath : ""
            );
            session.sendMessage(new TextMessage(command));
            log.info("发送扫描指令: probeKey={}, scanPath={}", probeKey, scanPath);
            return true;
        } catch (Exception e) {
            log.error("发送扫描指令失败", e);
            return false;
        }
    }

    /**
     * 发送确认响应
     */
    public void sendAck(String probeKey, boolean success, String message) {
        WebSocketSession session = sessions.get(probeKey);
        if (session != null && session.isOpen()) {
            try {
                String response = String.format(
                    "{\"type\":\"ACK\",\"success\":%s,\"message\":\"%s\"}",
                    success, message
                );
                session.sendMessage(new TextMessage(response));
            } catch (Exception e) {
                log.error("发送确认响应失败", e);
            }
        }
    }

    /**
     * 发送错误消息
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            String message = String.format("{\"type\":\"ERROR\",\"message\":\"%s\"}", errorMessage);
            session.sendMessage(new TextMessage(message));
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }

    // ========== 会话管理方法 ==========

    /**
     * 获取会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 获取所有在线探针KEY
     */
    public List<String> getOnlineProbeKeys() {
        return List.copyOf(sessions.keySet());
    }

    /**
     * 检查探针是否在线
     */
    public boolean isOnline(String probeKey) {
        WebSocketSession session = sessions.get(probeKey);
        return session != null && session.isOpen();
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从URI中提取probe_key
     */
    private String extractProbeKey(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri != null) {
                String query = uri.getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2 && "probe_key".equals(pair[0])) {
                            return pair[1];
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("提取probe_key失败", e);
        }
        return null;
    }

    /**
     * 清理不活跃的WebSocket会话
     * 防止内存泄漏
     */
    private void cleanupInactiveSessions() {
        long currentTime = System.currentTimeMillis();
        int cleanedCount = 0;

        for (Map.Entry<String, Long> entry : sessionLastActivity.entrySet()) {
            String probeKey = entry.getKey();
            Long lastActivity = entry.getValue();

            if (currentTime - lastActivity > SESSION_TIMEOUT_MS) {
                WebSocketSession session = sessions.get(probeKey);
                if (session != null && session.isOpen()) {
                    try {
                        session.close();
                        log.info("关闭超时的WebSocket会话: probeKey={}, 不活跃时长: {}ms",
                                probeKey, currentTime - lastActivity);
                    } catch (Exception e) {
                        log.error("关闭超时会话失败: probeKey={}", probeKey, e);
                    }
                }

                // 从映射中移除
                sessions.remove(probeKey);
                sessionLastActivity.remove(probeKey);
                cleanedCount++;
            }
        }

        if (cleanedCount > 0) {
            log.info("已清理 {} 个超时的WebSocket会话，当前活跃会话数: {}",
                    cleanedCount, sessions.size());
        }
    }

    /**
     * 清理资源，在Bean销毁时调用
     */
    @PreDestroy
    public void destroy() {
        log.info("关闭WebSocket会话清理器");

        // 关闭所有活跃的会话
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            String probeKey = entry.getKey();
            WebSocketSession session = entry.getValue();
            if (session != null && session.isOpen()) {
                try {
                    session.close();
                    log.info("关闭WebSocket会话: probeKey={}", probeKey);
                } catch (Exception e) {
                    log.error("关闭WebSocket会话失败: probeKey={}", probeKey, e);
                }
            }
        }

        // 清空映射
        sessions.clear();
        sessionLastActivity.clear();

        // 关闭调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("WebSocket会话清理器已关闭");
    }
}
