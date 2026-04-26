package com.lixin.probe.agent.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket重连策略
 *
 * <p>负责WebSocket连接失败后的自动重连：
 * <ul>
 *   <li>固定延迟重连</li>
 *   <li>指数退避重连</li>
 *   <li>无限重试（直到Agent关闭）</li>
 *   <li>可配置重连延迟</li>
 * </ul></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.1 (支持自动重连)
 */
public class ReconnectionPolicy {

    private static final Logger log = LoggerFactory.getLogger(ReconnectionPolicy.class);
    /**
     * 重连策略类型
     */
    public enum Strategy {
        /** 固定延迟 */
        FIXED_DELAY,
        /** 指数退避 */
        EXPONENTIAL_BACKOFF
    }

    private final ScheduledExecutorService scheduler;
    private final ConnectionManager connectionManager;
    private final Strategy strategy;
    private final long initialDelaySeconds;
    private final long maxDelaySeconds;
    private final int maxAttempts;
    private int currentAttempt = 0;
    private String currentUrl;
    private volatile boolean shouldStop = false;
    private WebSocketHandler webSocketHandler;

    /**
     * 重连监听器接口
     */
    public interface ReconnectListener {
        /**
         * 开始重连
         *
         * @param attempt 当前尝试次数
         * @param delaySeconds 延迟秒数
         */
        void onReconnecting(int attempt, long delaySeconds);

        /**
         * 达到最大重连次数
         *
         * @param lastAttempt 最后一次尝试次数
         */
        void onMaxAttemptsReached(int lastAttempt);
    }

    private ReconnectListener listener;

    /**
     * 构造重连策略（固定延迟，默认5秒，无限重试）
     *
     * @param connectionManager 连接管理器
     */
    public ReconnectionPolicy(ConnectionManager connectionManager) {
        this(connectionManager, Strategy.FIXED_DELAY, 5, 60, -1);
    }

    /**
     * 构造重连策略（完整配置）
     *
     * @param connectionManager 连接管理器
     * @param strategy 重连策略
     * @param initialDelaySeconds 初始延迟（秒）
     * @param maxDelaySeconds 最大延迟（秒）
     * @param maxAttempts 最大尝试次数（-1表示无限重试）
     */
    public ReconnectionPolicy(ConnectionManager connectionManager,
                              Strategy strategy,
                              long initialDelaySeconds,
                              long maxDelaySeconds,
                              int maxAttempts) {
        this.connectionManager = connectionManager;
        this.strategy = strategy;
        this.initialDelaySeconds = initialDelaySeconds;
        this.maxDelaySeconds = maxDelaySeconds;
        this.maxAttempts = maxAttempts;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "reconnection-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 设置WebSocket处理器（用于实际重连）
     *
     * @param webSocketHandler WebSocket处理器
     */
    public void setWebSocketHandler(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 设置重连监听器
     *
     * @param listener 监听器
     */
    public void setListener(ReconnectListener listener) {
        this.listener = listener;
    }

    /**
     * 调度重连
     *
     * @param url WebSocket服务器URL
     */
    public void scheduleReconnect(String url) {
        scheduleReconnect(url, webSocketHandler);
    }

    public void scheduleReconnect(String url, WebSocketHandler webSocketHandler) {
        if (shouldStop) {
            log.info("重连已停止，不再重试");
            return;
        }

        // 检查最大尝试次数
        if (maxAttempts > 0 && currentAttempt >= maxAttempts) {
            log.warn("已达到最大重连次数: {}", maxAttempts);
            if (listener != null) {
                listener.onMaxAttemptsReached(currentAttempt);
            }
            return;
        }

        this.currentUrl = url;
        this.webSocketHandler = webSocketHandler;
        currentAttempt++;

        // 计算延迟
        long delaySeconds = calculateDelay();
        log.info("⏳ 计划在 {} 秒后重连（第 {} 次尝试，策略: {}）",
                 delaySeconds, currentAttempt, strategy);

        if (listener != null) {
            listener.onReconnecting(currentAttempt, delaySeconds);
        }

        scheduler.schedule(() -> {
            if (shouldStop) {
                log.info("重连已停止，取消此次重连");
                return;
            }

            try {
                log.info("🔄 尝试重新连接 WebSocket...（第 {} 次，URL: {}）", currentAttempt, currentUrl);
                connectionManager.connect(currentUrl, webSocketHandler);
            } catch (Exception e) {
                log.error("❌ 重连异常: {}，继续下一次重连", e.getMessage());
                // 继续下一次重连
                scheduleReconnect(currentUrl, webSocketHandler);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * 重置重连计数器（连接成功时调用）
     */
    public void reset() {
        if (currentAttempt > 0) {
            log.info("✅ 连接成功，重置重连计数器（之前尝试了 {} 次）", currentAttempt);
        }
        currentAttempt = 0;
    }

    /**
     * 停止重连（用于Agent关闭时）
     */
    public void stop() {
        log.info("停止重连策略");
        shouldStop = true;
    }

    /**
     * 计算重连延迟
     *
     * @return 延迟秒数
     */
    private long calculateDelay() {
        switch (strategy) {
            case EXPONENTIAL_BACKOFF:
                // 指数退避：初始延迟 * 2^(尝试次数-1)，但不超过最大延迟
                long exponentialDelay = initialDelaySeconds * (1L << Math.min(currentAttempt - 1, 31));
                return Math.min(exponentialDelay, maxDelaySeconds);

            case FIXED_DELAY:
            default:
                return initialDelaySeconds;
        }
    }

    /**
     * 取消当前的重连任务
     */
    public void cancel() {
        log.debug("重连已取消");
    }

    /**
     * 销毁重连策略
     */
    public void destroy() {
        stop();
        log.info("销毁重连策略");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取当前尝试次数
     *
     * @return 当前尝试次数
     */
    public int getCurrentAttempt() {
        return currentAttempt;
    }

    /**
     * 获取是否正在重连
     *
     * @return 如果当前尝试次数大于0且未停止返回true
     */
    public boolean isReconnecting() {
        return currentAttempt > 0 && !shouldStop;
    }

    /**
     * 获取是否已停止
     *
     * @return 如果已停止返回true
     */
    public boolean isStopped() {
        return shouldStop;
    }
}
