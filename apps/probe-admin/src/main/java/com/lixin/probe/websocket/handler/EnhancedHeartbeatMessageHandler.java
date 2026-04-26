package com.lixin.probe.websocket.handler;

import com.lixin.probe.websocket.SecureWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 心跳消息处理器（带 ACK 确认）
 * 增强版：支持心跳确认、延迟检测、质量统计
 *
 * @author Claude Code
 * @version 2.0 - ACK 增强版
 */
@Component
public class EnhancedHeartbeatMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(EnhancedHeartbeatMessageHandler.class);

    // 心跳时间戳记录（用于 ACK 验证）
    private final Map<String, Long> heartbeatTimestamps = new ConcurrentHashMap<>();

    // 心跳响应时间统计
    private final Map<String, Long> heartbeatResponseTimes = new ConcurrentHashMap<>();

    // 心跳统计
    private final Map<String, HeartbeatStats> statsMap = new ConcurrentHashMap<>();

    /**
     * 处理心跳消息并返回 ACK
     *
     * @param session WebSocket Session
     * @param message  心跳消息
     * @return        ACK 响应消息
     */
    public TextMessage handleHeartbeat(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        long receiveTime = Instant.now().toEpochMilli();

        try {
            JSONObject json = JSON.parseObject(message.getPayload());
            String type = json.getString("type");
            String cmd = json.getString("cmd");
            String code = json.getString("code");
            String key = json.getString("key");

            if (!"HEARTBEAT".equals(cmd)) {
                log.warn("收到非心跳消息，cmd={}", cmd);
                return null;
            }

            log.debug("收到心跳：sessionId={}, code={}, key={}", sessionId, code, maskKey(key));

            // 记录心跳时间戳
            heartbeatTimestamps.put(key, receiveTime);

            // 更新统计信息
            updateStats(sessionId, key);

            // 构建响应时间记录
            JSONObject response = new JSONObject();
            response.put("type", "RESPONSE");
            response.put("cmd", "HEARTBEAT_ACK");
            response.put("code", code);
            response.put("key", key);
            response.put("timestamp", receiveTime);
            response.put("serverTime", Instant.now().toEpochMilli());

            // 添加响应时间（如果之前有发送记录）
            Long lastSendTime = heartbeatResponseTimes.get(key);
            if (lastSendTime != null) {
                long responseTime = receiveTime - lastSendTime;
                response.put("responseTime", responseTime);
                response.put("responseTimeMs", responseTime);

                // 告警：响应时间过长
                if (responseTime > 5000) {
                    log.warn("心跳响应时间过长：key={}, responseTime={}ms", key, responseTime);
                }
            }

            log.debug("发送心跳 ACK：sessionId={}, key={}", sessionId, key);

            return new TextMessage(response.toJSONString());

        } catch (Exception e) {
            log.error("处理心跳消息失败：sessionId={}", sessionId, e);
            return createErrorMessage("心跳处理失败");
        }
    }

    /**
     * 处理心跳 ACK 响应
     *
     * @param session WebSocket Session
     * @param message  ACK 消息
     */
    public void handleHeartbeatAck(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        long receiveTime = Instant.now().toEpochMilli();

        try {
            JSONObject json = JSON.parseObject(message.getPayload());
            String cmd = json.getString("cmd");
            String key = json.getString("key");

            if (!"HEARTBEAT_ACK".equals(cmd)) {
                return;
            }

            // 计算往返时间
            Long sendTime = heartbeatTimestamps.get(key);
            if (sendTime != null) {
                long rtt = receiveTime - sendTime;
                heartbeatResponseTimes.put(key, receiveTime);

                log.debug("收到心跳 ACK：sessionId={}, key={}, rtt={}ms", sessionId, key, rtt);

                // 更新统计
                HeartbeatStats stats = statsMap.get(sessionId);
                if (stats != null) {
                    stats.updateRtt(rtt);
                }
            }

        } catch (Exception e) {
            log.error("处理心跳 ACK 失败：sessionId={}", sessionId, e);
        }
    }

    /**
     * 检查心跳超时
     *
     * @param key        探针密钥
     * @param timeoutMs  超时时间（毫秒）
     * @return           是否超时
     */
    public boolean isHeartbeatTimeout(String key, long timeoutMs) {
        Long lastHeartbeat = heartbeatTimestamps.get(key);
        if (lastHeartbeat == null) {
            return true;  // 从未收到心跳
        }

        long elapsed = Instant.now().toEpochMilli() - lastHeartbeat;
        return elapsed > timeoutMs;
    }

    /**
     * 获取最后心跳时间
     *
     * @param key 探针密钥
     * @return    最后心跳时间戳
     */
    public Long getLastHeartbeatTime(String key) {
        return heartbeatTimestamps.get(key);
    }

    /**
     * 清理会话相关数据
     *
     * @param sessionId 会话 ID
     */
    public void cleanupSession(String sessionId) {
        // 清理统计信息
        HeartbeatStats stats = statsMap.remove(sessionId);
        if (stats != null) {
            log.info("心跳统计：sessionId={}, totalHeartbeats={}, avgRtt={}ms",
                    sessionId, stats.getTotalHeartbeats(), stats.getAvgRtt());
        }
    }

    /**
     * 清理探针相关数据
     *
     * @param key 探针密钥
     */
    public void cleanupProbe(String key) {
        heartbeatTimestamps.remove(key);
        heartbeatResponseTimes.remove(key);
    }

    /**
     * 更新统计信息
     */
    private void updateStats(String sessionId, String key) {
        HeartbeatStats stats = statsMap.computeIfAbsent(sessionId, k -> new HeartbeatStats());
        stats.incrementHeartbeat();
    }

    /**
     * 创建错误消息
     */
    private TextMessage createErrorMessage(String error) {
        JSONObject response = new JSONObject();
        response.put("type", "ERROR");
        response.put("message", error);
        return new TextMessage(response.toJSONString());
    }

    /**
     * 掩盖密钥（用于日志）
     */
    private String maskKey(String key) {
        if (key == null || key.isEmpty()) {
            return "null";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    /**
     * 心跳统计信息
     */
    private static class HeartbeatStats {
        private long totalHeartbeats = 0;
        private long totalRtt = 0;
        private long maxRtt = 0;
        private long minRtt = Long.MAX_VALUE;

        public synchronized void incrementHeartbeat() {
            totalHeartbeats++;
        }

        public synchronized void updateRtt(long rtt) {
            totalRtt += rtt;
            if (rtt > maxRtt) maxRtt = rtt;
            if (rtt < minRtt) minRtt = rtt;
        }

        public long getTotalHeartbeats() {
            return totalHeartbeats;
        }

        public long getAvgRtt() {
            return totalHeartbeats > 0 ? totalRtt / totalHeartbeats : 0;
        }

        public long getMaxRtt() {
            return maxRtt;
        }

        public long getMinRtt() {
            return minRtt == Long.MAX_VALUE ? 0 : minRtt;
        }
    }
}
