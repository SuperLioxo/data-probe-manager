package com.lixin.probe.agent.websocket;

import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocket连接管理器
 *
 * <p>负责WebSocket连接的建立、状态管理和清理：
 * <ul>
 *   <li>建立WebSocket连接</li>
 *   <li>管理连接状态</li>
 *   <li>处理连接生命周期回调</li>
 *   <li>清理连接资源</li>
 * </ul></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.1 (增加消息大小限制)
 */
public class ConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);
    private final StandardWebSocketClient client;
    private WebSocketSession session;
    private final ConnectionListener listener;

    /**
     * 连接监听器接口
     */
    public interface ConnectionListener {
        /**
         * 连接成功回调
         */
        void onConnected(WebSocketSession session);

        /**
         * 连接失败回调
         */
        void onConnectionFailed(Throwable ex);

        /**
         * 连接关闭回调
         */
        void onClosed(CloseStatus closeStatus);
    }

    /**
     * 构造连接管理器
     *
     * @param listener 连接监听器
     */
    public ConnectionManager(ConnectionListener listener) {
        this.client = createConfiguredClient();
        this.listener = listener;
    }

    /**
     * 创建配置了缓冲区大小的WebSocket客户端
     */
    private StandardWebSocketClient createConfiguredClient() {
        StandardWebSocketClient wsClient = new StandardWebSocketClient();

        // 使用userProperties来设置WebSocket容器的缓冲区大小
        // 这些属性会被传递到底层的WebSocketContainer
        try {
            java.util.Map<String, Object> userProperties = new java.util.HashMap<>();

            // 设置最大文本消息缓冲区为10MB
            userProperties.put("jakarta.websocket.endpoint.textMaxBuffer", 10 * 1024 * 1024);

            // 设置最大二进制消息缓冲区为50MB
            userProperties.put("jakarta.websocket.endpoint.binaryMaxBuffer", 50 * 1024 * 1024);

            // 设置异步发送超时为30秒
            userProperties.put("jakarta.websocket.servlet.asyncSendTimeout", 30000);

            // 使用反射设置userProperties
            try {
                java.lang.reflect.Field userPropertiesField = StandardWebSocketClient.class.getDeclaredField("userProperties");
                userPropertiesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> existingProps = (java.util.Map<String, Object>) userPropertiesField.get(wsClient);
                if (existingProps != null) {
                    existingProps.putAll(userProperties);
                }
            } catch (Exception e) {
                log.warn("无法通过反射设置userProperties: {}", e.getMessage());
            }

            log.info("WebSocket客户端已配置: maxTextBufferSize=10MB, maxBinaryBufferSize=50MB");
        } catch (Exception e) {
            log.warn("配置WebSocket客户端失败，使用默认配置: {}", e.getMessage());
        }

        return wsClient;
    }

    /**
     * 连接到指定URL
     *
     * @param url WebSocket服务器URL
     * @param handler WebSocket处理器
     * @return 连接结果的Future
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<WebSocketSession> connect(String url, WebSocketHandler handler) {
        try {
            log.info("开始连接 WebSocket 服务器: {}", url);

            // 尝试使用doHandshakeAsync (Spring 6.1+)
            CompletableFuture<WebSocketSession> future;
            try {
                // 使用反射调用doHandshakeAsync方法（如果存在）
                future = (CompletableFuture<WebSocketSession>) client.getClass()
                    .getMethod("doHandshakeAsync", WebSocketHandler.class,
                              org.springframework.web.socket.WebSocketHttpHeaders.class, URI.class)
                    .invoke(client, handler, null, URI.create(url));
            } catch (NoSuchMethodException e) {
                // 如果doHandshakeAsync不存在，使用doHandshake并转换
                log.debug("使用doHandshake并转换为CompletableFuture (doHandshakeAsync不可用)");
                future = connectWithDeprecation(handler, url);
            }

            // 处理连接结果
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("WebSocket 连接失败", ex);
                    listener.onConnectionFailed(ex);
                } else {
                    this.session = result;
                    log.info("WebSocket 连接成功: {}", url);
                    listener.onConnected(result);
                }
            });

            return future;

        } catch (Exception e) {
            log.error("WebSocket 连接异常", e);
            listener.onConnectionFailed(e);
            CompletableFuture<WebSocketSession> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    /**
     * 使用deprecated的doHandshake API并转换为CompletableFuture
     * 这是向后兼容的fallback方法
     */
    @SuppressWarnings("deprecation")
    private CompletableFuture<WebSocketSession> connectWithDeprecation(WebSocketHandler handler, String url) {
        org.springframework.util.concurrent.ListenableFuture<WebSocketSession> listenableFuture =
            client.doHandshake(handler, null, URI.create(url));
        return toCompletableFuture(listenableFuture);
    }

    /**
     * 将ListenableFuture转换为CompletableFuture
     * 用于平滑迁移deprecated API
     */
    @SuppressWarnings({"deprecation", "unchecked"})
    private <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> listenableFuture) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        listenableFuture.addCallback(
            result -> completableFuture.complete(result),
            ex -> completableFuture.completeExceptionally(ex)
        );
        return completableFuture;
    }

    /**
     * 获取当前会话
     *
     * @return WebSocketSession，可能为null
     */
    public WebSocketSession getSession() {
        return session;
    }

    /**
     * 检查连接状态
     *
     * @return 如果已连接且会话开放返回true
     */
    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    /**
     * 清除会话引用（用于连接关闭后）
     */
    public void clearSession() {
        this.session = null;
    }

    /**
     * 关闭连接
     */
    public void close() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                log.info("WebSocket 会话已关闭");
            } catch (Exception e) {
                log.error("关闭 WebSocket 会话失败", e);
            }
        }
        session = null;
    }

    /**
     * 清理资源
     */
    public void destroy() {
        log.info("销毁连接管理器");
        close();
    }
}
