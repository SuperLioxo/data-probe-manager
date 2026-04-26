package com.lixin.probe.websocket;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.dto.MetricsData;
import com.lixin.probe.service.MetricsPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 实时指标推送WebSocket处理器
 *
 * 功能：
 * 1. 处理前端连接请求
 * 2. Token验证
 * 3. 实时推送探针指标数据
 * 4. 处理订阅/取消订阅探针
 *
 * @author Claude Code
 * @date 2026-04-13
 */
@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MetricsWebSocketHandler.class);

    @Autowired
    private MetricsPushService metricsPushService;

    // 存储所有连接的会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 存储会话订阅的探针ID列表
    private final Map<String, java.util.Set<Long>> subscribedProbes = new ConcurrentHashMap<>();

    // 定时推送任务
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // 推送间隔（秒）
    private static final int PUSH_INTERVAL_SECONDS = 10;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket连接建立: sessionId={}", session.getId());

        // 获取连接URL和参数
        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            log.debug("WebSocket连接查询参数: {}", query);

            // 验证token
            String token = extractToken(query);
            if (token != null && validateToken(token)) {
                // 注册会话
                sessions.put(session.getId(), session);
                subscribedProbes.put(session.getId(), ConcurrentHashMap.newKeySet());

                log.info("WebSocket会话已注册: sessionId={}, 总连接数={}",
                    session.getId(), sessions.size());
            } else {
                log.warn("Token验证失败，关闭连接: sessionId={}", session.getId());
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到WebSocket消息: sessionId={}, message={}", session.getId(), payload);

        try {
            // 解析消息
            Map<String, Object> messageObj = JSON.parseObject(payload, Map.class);
            String type = (String) messageObj.get("type");

            switch (type) {
                case "PING":
                    // 心跳检测
                    sendMessage(session, createMessage("PONG", null));
                    break;

                case "SUBSCRIBE":
                    // 订阅探针
                    handleSubscribe(session, messageObj);
                    break;

                case "UNSUBSCRIBE":
                    // 取消订阅探针
                    handleUnsubscribe(session, messageObj);
                    break;

                default:
                    log.warn("未知的消息类型: type={}", type);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败: sessionId={}, message={}", session.getId(), payload, e);
            sendError(session, "Invalid message format");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("WebSocket连接关闭: sessionId={}, status={}", session.getId(), closeStatus);

        // 清理会话
        sessions.remove(session.getId());
        subscribedProbes.remove(session.getId());

        log.info("WebSocket会话已清理，当前连接数: {}", sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误: sessionId={}", session.getId(), exception);
    }

    private void handleSubscribe(WebSocketSession session, Map<String, Object> messageObj) {
        updateSubscription(session, messageObj, "subscribe");
    }

    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> messageObj) {
        updateSubscription(session, messageObj, "unsubscribe");
    }

    @SuppressWarnings("unchecked")
    private void updateSubscription(WebSocketSession session, Map<String, Object> messageObj, String action) {
        try {
            Map<String, Object> payload = (Map<String, Object>) messageObj.get("payload");
            java.util.List<Number> rawIds = (java.util.List<Number>) payload.get("probeIds");
            java.util.List<Long> probeIds = rawIds != null
                ? rawIds.stream().map(Number::longValue).toList()
                : java.util.List.of();

            if (probeIds.isEmpty()) return;

            java.util.Set<Long> currentSubscriptions = subscribedProbes.get(session.getId());
            boolean isSubscribe = "subscribe".equals(action);
            if (isSubscribe) {
                currentSubscriptions.addAll(probeIds);
            } else {
                currentSubscriptions.removeAll(probeIds);
            }

            String ackType = isSubscribe ? "SUBSCRIBE_ACK" : "UNSUBSCRIBE_ACK";
            log.info("会话{}探针: sessionId={}, probeIds={}, 订阅数={}",
                isSubscribe ? "订阅" : "取消订阅", session.getId(), probeIds, currentSubscriptions.size());

            sendMessage(session, createMessage(ackType, Map.of(
                "probeIds", probeIds, "count", probeIds.size()
            )));
        } catch (Exception e) {
            log.error("处理{}请求失败", action, e);
            sendError(session, action + " failed");
        }
    }

    /**
     * 推送指标更新到所有订阅的会话
     */
    public void pushMetricsUpdate(Long probeId, MetricsData metricsData) {
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            java.util.Set<Long> subscriptions = subscribedProbes.get(entry.getKey());

            if (subscriptions != null && subscriptions.contains(probeId)) {
                try {
                    if (session.isOpen()) {
                        Map<String, Object> payload = Map.of(
                            "probeId", probeId,
                            "metrics", metricsData
                        );
                        sendMessage(session, createMessage("METRICS_UPDATE", payload));
                    }
                } catch (Exception e) {
                    log.error("推送指标数据失败: sessionId={}, probeId={}", entry.getKey(), probeId, e);
                }
            }
        }
    }

    /**
     * 推送探针状态更新
     */
    public void pushStatusUpdate(Long probeId, String status) {
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            java.util.Set<Long> subscriptions = subscribedProbes.get(entry.getKey());

            if (subscriptions != null && subscriptions.contains(probeId)) {
                try {
                    if (session.isOpen()) {
                        Map<String, Object> payload = Map.of(
                            "probeId", probeId,
                            "status", status
                        );
                        sendMessage(session, createMessage("PROBE_STATUS", payload));
                    }
                } catch (Exception e) {
                    log.error("推送状态更新失败: sessionId={}, probeId={}", entry.getKey(), probeId, e);
                }
            }
        }
    }

    /**
     * 推送告警
     */
    public void pushAlert(String message, String level) {
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            try {
                if (session.isOpen()) {
                    Map<String, Object> payload = Map.of(
                        "message", message,
                        "level", level,
                        "timestamp", System.currentTimeMillis()
                    );
                    sendMessage(session, createMessage("ALERT", payload));
                }
            } catch (Exception e) {
                log.error("推送告警失败: sessionId={}", entry.getKey(), e);
            }
        }
    }

    /**
     * 启动定期推送任务
     */
    public void startPeriodicPush() {
        scheduler.scheduleAtFixedRate(this::periodicPushAllMetrics,
            PUSH_INTERVAL_SECONDS, PUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("启动定期推送任务: 间隔={}秒", PUSH_INTERVAL_SECONDS);
    }

    /**
     * 停止定期推送任务
     */
    public void stopPeriodicPush() {
        scheduler.shutdown();
        log.info("停止定期推送任务");
    }

    private void cleanStaleSessions() {
        sessions.entrySet().removeIf(entry -> {
            if (!entry.getValue().isOpen()) {
                subscribedProbes.remove(entry.getKey());
                log.info("清理已关闭的WebSocket会话: sessionId={}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * 定期推送所有订阅探针的指标数据
     */
    private void periodicPushAllMetrics() {
        try {
            cleanStaleSessions();

            if (sessions.isEmpty()) {
                return;
            }

            log.debug("开始定期推送指标数据，连接数={}", sessions.size());

            // 获取所有被订阅的探针ID
            java.util.Set<Long> allSubscribedProbes = new java.util.HashSet<>();
            for (java.util.Set<Long> probes : subscribedProbes.values()) {
                allSubscribedProbes.addAll(probes);
            }

            // 推送指标数据
            for (Long probeId : allSubscribedProbes) {
                MetricsData metricsData = metricsPushService.collectMetrics(probeId);
                if (metricsData != null) {
                    pushMetricsUpdate(probeId, metricsData);
                }
            }

            log.debug("定期推送完成，推送了 {} 个探针的数据", allSubscribedProbes.size());
        } catch (Exception e) {
            log.error("定期推送指标数据失败", e);
        }
    }

    /**
     * 获取当前活动连接数
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * 发送消息到会话
     */
    private void sendMessage(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.error("发送WebSocket消息失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 发送错误消息
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            session.sendMessage(new TextMessage(createMessage("ERROR", Map.of("message", errorMessage))));
        } catch (IOException e) {
            log.error("发送错误消息失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 创建消息
     */
    private String createMessage(String type, Object payload) {
        Map<String, Object> message = Map.of(
            "type", type,
            "payload", payload,
            "timestamp", System.currentTimeMillis()
        );
        return JSON.toJSONString(message);
    }

    /**
     * 从查询参数中提取token
     */
    private String extractToken(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        String[] params = query.split("&");
        for (String param : params) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }

    /**
     * 验证token
     */
    private boolean validateToken(String token) {
        // TODO: 实际的JWT验证逻辑
        // 目前简单检查token是否为空
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        try {
            // 这里可以调用JWT验证服务
            // JwtTokenUtil.validateToken(token);
            return true;
        } catch (Exception e) {
            log.warn("Token验证失败: {}", e.getMessage());
            return false;
        }
    }
}