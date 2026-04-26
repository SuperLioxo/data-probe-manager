package com.lixin.probe.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * WebSocket Origin 验证拦截器
 * 防止 CSRF 攻击和未授权的跨域连接
 *
 * @author Claude Code
 * @version 2.0
 */
@Component
public class WebSocketOriginInterceptor implements WebSocketHandlerDecoratorFactory {

    private static final Logger log = LoggerFactory.getLogger(WebSocketOriginInterceptor.class);

    @Value("${websocket.allowed-origins:}")
    private String allowedOriginsConfig;

    @Value("${websocket.block-unlisted-origins:true}")
    private boolean blockUnlistedOrigins;

    @Value("${websocket.enable-origin-logging:true}")
    private boolean enableOriginLogging;

    // 白名单：允许的 Origin
    private final Set<String> allowedOrigins = new HashSet<>();

    // 黑名单：明确拒绝的 Origin
    private final Set<String> blockedOrigins = new HashSet<>(
        Arrays.asList(
            "null",                          // 防止 null origin
            "undefined",                     // 防止 undefined origin
            "<local-file>"                  // 防止本地文件访问
        )
    );

    /**
     * 装饰 WebSocket Handler，添加 Origin 验证
     */
    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new OriginValidatingHandler(handler);
    }

    /**
     * Origin 验证的 WebSocket Handler 装饰器
     */
    private class OriginValidatingHandler extends TextWebSocketHandler {

        private final WebSocketHandler delegate;

        public OriginValidatingHandler(WebSocketHandler delegate) {
            this.delegate = delegate;
            // 初始化允许的 Origin
            initAllowedOrigins();
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            String origin = getOriginFromSession(session);

            // 验证 Origin
            if (!validateOrigin(origin, session)) {
                log.warn("WebSocket 连接被拒绝：无效的 Origin - {}", origin);
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid Origin"));
                return;
            }

            // Origin 验证通过，允许连接
            if (enableOriginLogging) {
                log.info("WebSocket 连接已建立：sessionId={}, origin={}, remoteAddress={}",
                        session.getId(), origin, session.getRemoteAddress());
            }

            // 继续正常的连接流程
            delegate.afterConnectionEstablished(session);
        }

        @Override
        public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            delegate.handleMessage(session, message);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
            if (enableOriginLogging) {
                log.info("WebSocket 连接已关闭：sessionId={}, closeStatus={}",
                        session.getId(), closeStatus);
            }
            delegate.afterConnectionClosed(session, closeStatus);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            log.error("WebSocket 传输错误：sessionId={}, origin={}",
                    session.getId(), getOriginFromSession(session), exception);
            delegate.handleTransportError(session, exception);
        }

        /**
         * 从 WebSocket Session 中提取 Origin
         */
        private String getOriginFromSession(WebSocketSession session) {
            try {
                URI uri = session.getUri();
                if (uri != null) {
                    String origin = uri.getScheme() + "://" + uri.getAuthority();
                    return origin;
                }
            } catch (Exception e) {
                log.debug("无法从 Session 提取 Origin: {}", session.getId());
            }

            // 尝试从 Session 属性中获取
            Object originAttr = session.getAttributes().get("origin");
            if (originAttr != null) {
                return originAttr.toString();
            }

            return "unknown";
        }

        /**
         * 验证 Origin 是否合法
         *
         * @param origin   请求的 Origin
         * @param session  WebSocket Session
         * @return        是否允许连接
         */
        private boolean validateOrigin(String origin, WebSocketSession session) {
            // 1. 检查黑名单
            if (blockedOrigins.contains(origin)) {
                log.warn("Origin 在黑名单中：{}", origin);
                return false;
            }

            // 2. 如果允许的列表为空，检查是否阻止未列出的 Origin
            if (allowedOrigins.isEmpty()) {
                if (blockUnlistedOrigins) {
                    // 只允许本地连接
                    boolean isLocal = isLocalOrigin(origin, session);
                    if (!isLocal) {
                        log.warn("未配置允许的 Origin，拒绝非本地连接：{}", origin);
                        return false;
                    }
                }
                return true;
            }

            // 3. 检查白名单
            if (allowedOrigins.contains(origin)) {
                return true;
            }

            // 4. 检查通配符匹配
            for (String allowedPattern : allowedOrigins) {
                if (allowedPattern.contains("*")) {
                    // 简单的通配符匹配（例如：*.example.com）
                    if (matchesWildcard(origin, allowedPattern)) {
                        return true;
                    }
                }
            }

            log.warn("Origin 不在允许列表中：{}，允许列表：{}", origin, allowedOrigins);
            return false;
        }

        /**
         * 检查是否为本地 Origin
         */
        private boolean isLocalOrigin(String origin, WebSocketSession session) {
            return "localhost".equals(origin) ||
                   "127.0.0.1".equals(origin) ||
                   origin.startsWith("127.0.0.1:") ||
                   origin.startsWith("localhost:") ||
                   origin.contains("localhost:") ||
                   isLocalNetworkAddress(origin);
        }

        /**
         * 检查是否为本地网络地址
         */
        private boolean isLocalNetworkAddress(String origin) {
            // 检查是否为 192.168.x.x 或 10.x.x.x
            return origin.matches("^.*/(192\\.168\\..*|10\\..*|172\\.1[6-9]\\..*|172\\.2[0-9]\\..*|172\\.3[01]\\..*)");
        }

        /**
         * 通配符匹配
         */
        private boolean matchesWildcard(String origin, String pattern) {
            if (!pattern.contains("*")) {
                return origin.equals(pattern);
            }

            // 简单的通配符替换
            String regex = pattern.replace(".", "\\.")
                               .replace("*", ".*");
            return origin.matches(regex);
        }

        /**
         * 初始化允许的 Origin 列表
         */
        private void initAllowedOrigins() {
            if (allowedOriginsConfig != null && !allowedOriginsConfig.trim().isEmpty()) {
                String[] origins = allowedOriginsConfig.split(",");
                for (String origin : origins) {
                    String trimmed = origin.trim();
                    if (!trimmed.isEmpty()) {
                        allowedOrigins.add(trimmed);
                    }
                }
                log.info("WebSocket 允许的 Origin 列表：{}", allowedOrigins);
            } else {
                log.info("未配置允许的 Origin，将使用本地连接策略");
            }
        }
    }
}
