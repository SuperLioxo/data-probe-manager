package com.lixin.probe.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket会话清理器
 * 定期清理无效的WebSocket会话，防止内存泄漏
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 1.0
 */
@Component
public class WebSocketSessionCleaner {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionCleaner.class);

    /**
     * 会话超时时间（毫秒），默认30分钟
     */
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;

    /**
     * 存储会话的创建时间
     */
    private final Map<String, Long> sessionCreationTimes = new ConcurrentHashMap<>();

    /**
     * 存储会话的最后活跃时间
     */
    private final Map<String, Long> sessionLastActiveTimes = new ConcurrentHashMap<>();

    /**
     * 注册会话
     *
     * @param sessionId 会话ID
     */
    public void registerSession(String sessionId) {
        long now = System.currentTimeMillis();
        sessionCreationTimes.put(sessionId, now);
        sessionLastActiveTimes.put(sessionId, now);
        log.debug("注册WebSocket会话: sessionId={}", sessionId);
    }

    /**
     * 更新会话活跃时间
     *
     * @param sessionId 会话ID
     */
    public void updateSessionActivity(String sessionId) {
        sessionLastActiveTimes.put(sessionId, System.currentTimeMillis());
        log.debug("更新WebSocket会话活跃时间: sessionId={}", sessionId);
    }

    /**
     * 注销会话
     *
     * @param sessionId 会话ID
     */
    public void unregisterSession(String sessionId) {
        sessionCreationTimes.remove(sessionId);
        sessionLastActiveTimes.remove(sessionId);
        log.debug("注销WebSocket会话: sessionId={}", sessionId);
    }

    /**
     * 检查会话是否超时
     *
     * @param sessionId 会话ID
     * @return true如果超时
     */
    public boolean isSessionExpired(String sessionId) {
        Long lastActiveTime = sessionLastActiveTimes.get(sessionId);
        if (lastActiveTime == null) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - lastActiveTime;
        return elapsed > SESSION_TIMEOUT_MS;
    }

    /**
     * 定期清理超时会话
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanupExpiredSessions() {
        try {
            long now = System.currentTimeMillis();
            int cleanedCount = 0;

            // 检查所有会话
            for (Map.Entry<String, Long> entry : sessionLastActiveTimes.entrySet()) {
                String sessionId = entry.getKey();
                Long lastActiveTime = entry.getValue();

                if (lastActiveTime == null) {
                    sessionCreationTimes.remove(sessionId);
                    sessionLastActiveTimes.remove(sessionId);
                    cleanedCount++;
                    continue;
                }

                long elapsed = now - lastActiveTime;
                if (elapsed > SESSION_TIMEOUT_MS) {
                    log.warn("检测到超时的WebSocket会话: sessionId={}, 超时时间={}ms",
                            sessionId, elapsed);

                    sessionCreationTimes.remove(sessionId);
                    sessionLastActiveTimes.remove(sessionId);
                    cleanedCount++;
                }
            }

            if (cleanedCount > 0) {
                log.info("清理了{}个超时的WebSocket会话", cleanedCount);
            }

            // 记录当前活跃会话数
            int activeSessions = sessionLastActiveTimes.size();
            log.debug("当前活跃WebSocket会话数: {}", activeSessions);

        } catch (Exception e) {
            log.error("清理WebSocket会话失败", e);
        }
    }

    /**
     * 获取会话统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("activeSessions", sessionLastActiveTimes.size());
        stats.put("timeoutMs", SESSION_TIMEOUT_MS);
        stats.put("timeoutMinutes", SESSION_TIMEOUT_MS / (60 * 1000));

        // 计算平均会话年龄
        long now = System.currentTimeMillis();
        long totalAge = 0;
        for (Long creationTime : sessionCreationTimes.values()) {
            totalAge += (now - creationTime);
        }
        long avgAgeMs = sessionCreationTimes.isEmpty() ? 0 : totalAge / sessionCreationTimes.size();
        stats.put("avgSessionAgeMinutes", avgAgeMs / (60 * 1000));

        return stats;
    }

    /**
     * 获取超时时间（分钟）
     *
     * @return 超时时间（分钟）
     */
    public long getTimeoutMinutes() {
        return SESSION_TIMEOUT_MS / (60 * 1000);
    }
}
