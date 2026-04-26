package com.lixin.probe.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket基础处理器
 * 提供会话管理、心跳检测、资源清理等通用功能
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 1.0
 */
public abstract class BaseWebSocketHandler extends TextWebSocketHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    protected WebSocketSessionCleaner sessionCleaner;

    // 存储连接的会话
    protected final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 存储会话的创建时间
    protected final Map<String, Long> sessionCreationTimes = new ConcurrentHashMap<>();

    /**
     * 心跳间隔（秒），默认30秒
     */
    protected static final int HEARTBEAT_INTERVAL = 30;

    /**
     * 会话超时时间（秒），默认30分钟
     */
    protected static final int SESSION_TIMEOUT_SECONDS = 30 * 60;

    /**
     * 获取会话的唯一标识符
     *
     * @param session WebSocket会话
     * @return 会话ID
     */
    protected abstract String getSessionId(WebSocketSession session);

    /**
     * 处理心跳消息
     *
     * @param session WebSocket会话
     */
    protected void handleHeartbeat(@NonNull WebSocketSession session) {
        String sessionId = getSessionId(session);
        if (sessionId != null) {
            sessionCleaner.updateSessionActivity(sessionId);
            log.debug("收到心跳: sessionId={}", sessionId);
        }
    }

    /**
     * 发送心跳消息
     *
     * @param session WebSocket会话
     */
    protected void sendHeartbeat(@NonNull WebSocketSession session) {
        try {
            if (session.isOpen()) {
                // 子类可以重写此方法来自定义心跳消息
                session.sendMessage(new TextMessage("{\"type\":\"HEARTBEAT\"}"));
            }
        } catch (IOException e) {
            log.error("发送心跳失败: sessionId={}", getSessionId(session), e);
            closeSession(session);
        }
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        String sessionId = getSessionId(session);
        if (sessionId != null) {
            sessions.put(sessionId, session);
            sessionCreationTimes.put(sessionId, System.currentTimeMillis());
            sessionCleaner.registerSession(sessionId);

            log.info("WebSocket连接建立: sessionId={}", sessionId);
        } else {
            log.warn("WebSocket连接建立失败：无法获取会话ID");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid session identifier"));
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        String sessionId = getSessionId(session);
        if (sessionId != null) {
            sessions.remove(sessionId);
            sessionCreationTimes.remove(sessionId);
            sessionCleaner.unregisterSession(sessionId);

            log.info("WebSocket连接关闭: sessionId={}, status={}", sessionId, status);
        }
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {
        String sessionId = getSessionId(session);
        log.error("WebSocket传输错误: sessionId={}", sessionId, exception);

        // 发生传输错误时关闭会话
        if (session.isOpen()) {
            closeSession(session);
        }
    }

    /**
     * 关闭会话
     *
     * @param session WebSocket会话
     */
    protected void closeSession(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR.withReason("Internal error"));
            }
        } catch (IOException e) {
            log.error("关闭WebSocket会话失败", e);
        }
    }

    /**
     * 发送消息到指定会话
     *
     * @param sessionId 会话ID
     * @param message 消息内容
     * @return true如果发送成功
     */
    protected boolean sendMessage(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            log.warn("会话不存在: sessionId={}", sessionId);
            return false;
        }

        if (!session.isOpen()) {
            log.warn("会话已关闭: sessionId={}", sessionId);
            sessions.remove(sessionId);
            return false;
        }

        try {
            session.sendMessage(new TextMessage(message));
            return true;
        } catch (IOException e) {
            log.error("发送消息失败: sessionId={}", sessionId, e);
            closeSession(session);
            return false;
        }
    }

    /**
     * 获取活跃会话数
     *
     * @return 活跃会话数
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * 获取所有会话ID
     *
     * @return 会话ID集合
     */
    public java.util.Set<String> getSessionIds() {
        return sessions.keySet();
    }

    /**
     * 检查会话是否存在且活跃
     *
     * @param sessionId 会话ID
     * @return true如果会话活跃
     */
    public boolean isSessionActive(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }

    /**
     * 清理所有会话
     * 用于服务关闭时的资源清理
     */
    public void cleanupAllSessions() {
        log.info("开始清理所有WebSocket会话，当前会话数: {}", sessions.size());

        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();

            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.GOING_AWAY.withReason("Server shutdown"));
                }
            } catch (IOException e) {
                log.error("关闭会话失败: sessionId={}", sessionId, e);
            }

            sessionCleaner.unregisterSession(sessionId);
        }

        sessions.clear();
        sessionCreationTimes.clear();
        log.info("所有WebSocket会话已清理");
    }
}
