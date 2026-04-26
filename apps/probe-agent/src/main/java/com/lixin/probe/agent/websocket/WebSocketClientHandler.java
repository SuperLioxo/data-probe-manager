package com.lixin.probe.agent.websocket;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.lixin.probe.agent.service.DatabaseService;
import com.lixin.probe.agent.service.FileService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket 客户端处理器（重构版）
 *
 * <p>此类已重构为轻量级的协调器，将职责委托给专门的组件：
 * <ul>
 *   <li>ConnectionManager - 管理连接生命周期</li>
 *   <li>MessageSender - 处理消息发送</li>
 *   <li>MessageHandler - 处理消息接收</li>
 *   <li>HeartbeatScheduler - 管理心跳调度</li>
 *   <li>ReconnectionPolicy - 管理重连策略</li>
 * </ul></p>
 *
 * <p>原401行代码已拆分为5个专职类，此类仅保留协调逻辑。</p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 2.1 (实现命令处理)
 */
@Component
public class WebSocketClientHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketClientHandler.class);

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private FileService fileService;

    @Autowired
    private com.lixin.probe.agent.sync.ProbeSyncService probeSyncService;

    @Autowired
    private com.lixin.probe.agent.config.DatabaseConfigManager databaseConfigManager;

    @Autowired
    private ProbeCommandHandler probeCommandHandler;

    @Autowired
    private ConfigUpdateHandler configUpdateHandler;

    @Autowired
    private UpgradeHandler upgradeHandler;

    @Autowired(required = false)
    private com.lixin.probe.agent.module.FileModule fileModule;

    private ConnectionManager connectionManager;
    private MessageSender messageSender;
    private MessageHandler messageHandler;
    private HeartbeatScheduler heartbeatScheduler;
    private ReconnectionPolicy reconnectionPolicy;

    /**
     * 初始化组件（在依赖注入后执行）
     */
    @PostConstruct
    public void init() {
        // 初始化组件
        this.connectionManager = new ConnectionManager(new ConnectionListener());
        this.messageSender = new MessageSender(connectionManager, new AgentConfigProviderImpl());

        // 获取加密密钥（使用原始key，不进行填充）
        String encryptionKey = agentProperties.getKey();
        log.info("初始化MessageHandler，加密密钥长度: {}", encryptionKey != null ? encryptionKey.length() : "null");

        // 初始化探针状态管理器
        com.lixin.probe.agent.probe.ProbeStateManager probeStateManager =
            new com.lixin.probe.agent.probe.ProbeStateManager();

        // 使用注入的ProbeCommandHandler
        probeCommandHandler.setMessageSender(messageSender);

        // 设置FileModule的MessageSender
        if (fileModule != null) {
            fileModule.setMessageSender(messageSender);
            log.info("FileModule的MessageSender已设置");
        }

        this.messageHandler = new MessageHandler(probeCommandHandler, encryptionKey, messageSender);

        // 设置配置热更新处理器
        configUpdateHandler.setMessageSender(messageSender);
        this.messageHandler.setConfigUpdateHandler(configUpdateHandler);

        // 设置升级处理器
        upgradeHandler.setMessageSender(messageSender);
        this.messageHandler.setUpgradeHandler(upgradeHandler);
        this.heartbeatScheduler = new HeartbeatScheduler(messageSender, probeSyncService, probeStateManager,
                                                         databaseConfigManager, 30);

        // 使用指数退避策略，2秒初始延迟，最大60秒，无限重试
        this.reconnectionPolicy = new ReconnectionPolicy(
            connectionManager,
            ReconnectionPolicy.Strategy.EXPONENTIAL_BACKOFF,
            2,  // 初始延迟2秒
            60, // 最大延迟60秒
            -1  // 无限重试
        );

        // 设置WebSocketHandler到重连策略（用于自动重连）
        reconnectionPolicy.setWebSocketHandler(this);

        // 设置重连监听器
        reconnectionPolicy.setListener(new ReconnectionPolicy.ReconnectListener() {
            @Override
            public void onReconnecting(int attempt, long delaySeconds) {
                log.info("准备重连（第 {} 次尝试），延迟 {} 秒", attempt, delaySeconds);
            }

            @Override
            public void onMaxAttemptsReached(int lastAttempt) {
                log.error("已达到最大重连次数（{} 次），停止重连", lastAttempt);
            }
        });
    }

    /**
     * 连接到服务器
     */
    public void connect() {
        String baseUrl = agentProperties.getServer().getWsMetaUrl();

        // 使用原始的探针key作为URL参数（不填充），这样Admin端可以正确识别探针
        String originalKey = agentProperties.getKey();

        // 对URL参数进行编码，避免特殊字符导致解析错误
        try {
            String encodedCode = java.net.URLEncoder.encode(agentProperties.getCode(), "UTF-8");
            String encodedKey = java.net.URLEncoder.encode(originalKey, "UTF-8");
            String url = baseUrl + "?code=" + encodedCode + "&probe_key=" + encodedKey;
            log.info("连接WebSocket: code={}, originalKey={}, url={}", agentProperties.getCode(), originalKey, url);
            connect(url);
        } catch (Exception e) {
            log.error("URL编码失败", e);
            // 降级到不编码的方式
            String url = baseUrl + "?code=" + agentProperties.getCode() + "&probe_key=" + originalKey;
            connect(url);
        }
    }

    /**
     * 连接到指定URL
     */
    public void connect(String url) {
        connectionManager.connect(url, this);
    }

    /**
     * 发送元数据
     */
    public void sendMetadata(ProbeResponse.Metadata metadata) {
        messageSender.sendMetadata(metadata);
    }

    /**
     * 发送数据量信息
     */
    public void sendDataSize(ProbeResponse.DataSize dataSize) {
        messageSender.sendDataSize(dataSize);
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connectionManager != null && connectionManager.isConnected();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        int previousAttempts = reconnectionPolicy.getCurrentAttempt();
        if (previousAttempts > 0) {
            log.info("✅ WebSocket 重连成功！（之前尝试了 {} 次）", previousAttempts);
        } else {
            log.info("✅ WebSocket 连接已建立");
        }
        reconnectionPolicy.reset();
    }

    @Override
    public void handleMessage(WebSocketSession session, org.springframework.web.socket.WebSocketMessage<?> springMessage) throws Exception {
        messageHandler.handleMessage(session, springMessage);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket 传输错误", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.warn("WebSocket 连接已关闭: {}", closeStatus);

        // 清除旧的会话引用，以便 isConnected() 能正确返回 false
        // 这样可以避免在多连接场景下的竞态条件
        if (connectionManager.getSession() == session) {
            // 只有当关闭的会话是当前会话时才清除
            // (避免多连接场景下一个连接关闭清除另一个连接的会话)
            connectionManager.clearSession();
        }

        // 只要Agent未主动停止，就尝试重连（包括NORMAL关闭，如Admin重启）
        if (!reconnectionPolicy.isStopped()) {
            try {
                // 检查是否已经重新连接（避免重复重连）
                if (connectionManager.isConnected()) {
                    log.info("已经重新连接，取消重连调度");
                    return;
                }

                // 使用原始key进行重连（不填充）
                String originalKey = agentProperties.getKey();
                String baseUrl = agentProperties.getServer().getWsMetaUrl();
                String encodedCode = java.net.URLEncoder.encode(agentProperties.getCode(), "UTF-8");
                String encodedKey = java.net.URLEncoder.encode(originalKey, "UTF-8");
                String url = baseUrl + "?code=" + encodedCode + "&probe_key=" + encodedKey;
                log.info("连接关闭，准备重连，closeCode={}, URL: {}, 尝试次数: {}",
                        closeStatus.getCode(), url, reconnectionPolicy.getCurrentAttempt() + 1);
                reconnectionPolicy.scheduleReconnect(url, this);
            } catch (Exception e) {
                log.error("构建重连URL失败", e);
            }
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    @PreDestroy
    public void destroy() {
        log.info("正在关闭 WebSocket 客户端...");

        // 停止重连
        if (reconnectionPolicy != null) {
            reconnectionPolicy.stop();
        }

        // 停止心跳
        if (heartbeatScheduler != null) {
            heartbeatScheduler.destroy();
        }

        // 关闭连接
        if (connectionManager != null) {
            connectionManager.destroy();
        }

        // 销毁重连策略
        if (reconnectionPolicy != null) {
            reconnectionPolicy.destroy();
        }

        log.info("WebSocket 客户端已关闭");
    }

    /**
     * 填充或截断字符串到16字节用于AES-128
     */
    private String padTo16Bytes(String input) {
        byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] key = new byte[16];
        System.arraycopy(bytes, 0, key, 0, Math.min(bytes.length, 16));
        return new String(key, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ==================== 内部类实现 ====================

    /**
     * 连接监听器实现
     */
    private class ConnectionListener implements ConnectionManager.ConnectionListener {
        @Override
        public void onConnected(WebSocketSession session) {
            messageSender.sendHandshake();
            heartbeatScheduler.start();
        }

        @Override
        public void onConnectionFailed(Throwable ex) {
            // 连接失败时触发重连（重连策略会处理退避和次数限制）
            if (!reconnectionPolicy.isStopped()) {
                log.warn("连接失败: {}, 触发重连", ex.getMessage());
                try {
                    String originalKey = agentProperties.getKey();
                    String baseUrl = agentProperties.getServer().getWsMetaUrl();
                    String encodedCode = java.net.URLEncoder.encode(agentProperties.getCode(), "UTF-8");
                    String encodedKey = java.net.URLEncoder.encode(originalKey, "UTF-8");
                    String url = baseUrl + "?code=" + encodedCode + "&probe_key=" + encodedKey;
                    reconnectionPolicy.scheduleReconnect(url, WebSocketClientHandler.this);
                } catch (Exception e) {
                    log.error("构建重连URL失败", e);
                }
            }
        }

        @Override
        public void onClosed(CloseStatus closeStatus) {
            heartbeatScheduler.stop();
        }
    }

    /**
     * Agent配置提供者实现
     */
    private class AgentConfigProviderImpl implements MessageSender.AgentConfigProvider {
        @Override
        public String getCode() {
            return agentProperties.getCode();
        }

        @Override
        public String getKey() {
            return agentProperties.getKey();
        }
    }
}
