package com.lixin.probe.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket连接池管理器
 * 管理WebSocket连接的生命周期和数量限制
 *
 * @author Claude Code
 * @date 2026-04-12
 */
@Component
public class WebSocketConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnectionPool.class);

    private static final int MAX_CONNECTIONS_PER_PROBE = 3;

    // 会话存储
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 每个探针的连接计数
    private final ConcurrentHashMap<String, AtomicInteger> probeConnections = new ConcurrentHashMap<>();

    // 统计信息
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    /**
     * 添加连接
     *
     * @param probeKey 探针Key
     * @param session WebSocket会话
     * @return 是否添加成功
     */
    public boolean addConnection(String probeKey, WebSocketSession session) {
        if (probeKey == null || session == null) {
            log.warn("无效的探针Key或会话");
            return false;
        }

        AtomicInteger count = probeConnections.computeIfAbsent(
                probeKey, k -> new AtomicInteger(0)
        );

        if (count.incrementAndGet() > MAX_CONNECTIONS_PER_PROBE) {
            count.decrementAndGet();
            log.warn("探针 {} 的连接数超过限制: {}", probeKey, MAX_CONNECTIONS_PER_PROBE);
            return false;
        }

        sessions.put(session.getId(), session);
        totalConnections.incrementAndGet();

        log.info("WebSocket连接已添加: probeKey={}, sessionId={}, 当前连接数={}",
                probeKey, session.getId(), count.get());

        return true;
    }

    /**
     * 移除连接
     *
     * @param probeKey 探针Key
     * @param sessionId 会话ID
     */
    public void removeConnection(String probeKey, String sessionId) {
        sessions.remove(sessionId);
        totalConnections.decrementAndGet();

        AtomicInteger count = probeConnections.get(probeKey);
        if (count != null) {
            int newCount = count.decrementAndGet();
            if (newCount <= 0) {
                probeConnections.remove(probeKey);
            }
        }

        log.info("WebSocket连接已移除: probeKey={}, sessionId={}", probeKey, sessionId);
    }

    /**
     * 获取连接数量
     *
     * @param probeKey 探针Key
     * @return 连接数量
     */
    public int getConnectionCount(String probeKey) {
        AtomicInteger count = probeConnections.get(probeKey);
        return count == null ? 0 : count.get();
    }

    /**
     * 获取所有已连接的探针
     *
     * @return 探针Key集合
     */
    public java.util.Set<String> getConnectedProbes() {
        return probeConnections.keySet();
    }

    /**
     * 获取总连接数
     *
     * @return 总连接数
     */
    public int getTotalConnections() {
        return totalConnections.get();
    }

    /**
     * 检查连接是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean hasConnection(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return WebSocket会话
     */
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 获取所有会话
     *
     * @return 会话集合
     */
    public java.util.Collection<WebSocketSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * 获取连接统计信息
     *
     * @return 统计信息
     */
    public ConnectionStats getStats() {
        return new ConnectionStats(
                totalConnections.get(),
                probeConnections.size(),
                MAX_CONNECTIONS_PER_PROBE
        );
    }

    /**
     * 清理所有连接
     */
    @PreDestroy
    public void cleanup() {
        log.info("清理WebSocket连接池: 总连接数={}", totalConnections.get());

        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (Exception e) {
                log.error("关闭WebSocket会话失败", e);
            }
        });

        sessions.clear();
        probeConnections.clear();
        totalConnections.set(0);
    }

    /**
     * 连接统计信息
     */
    public record ConnectionStats(
            int totalConnections,
            int connectedProbes,
            int maxConnectionsPerProbe
    ) {
        public double getAverageConnectionsPerProbe() {
            return connectedProbes == 0 ? 0 : (double) totalConnections / connectedProbes;
        }
    }
}
