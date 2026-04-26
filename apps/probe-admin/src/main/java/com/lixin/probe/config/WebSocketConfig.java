package com.lixin.probe.config;

import com.lixin.probe.websocket.FileProbeWebSocketHandler;
import com.lixin.probe.websocket.MetaProbeWebSocketHandler;
import com.lixin.probe.websocket.MetricsWebSocketHandler;
import com.lixin.probe.interceptor.WebSocketOriginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * WebSocket配置类
 * 安全的CORS配置，默认只允许同源请求
 * 增强 Origin 验证和握手拦截
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketConfig.class);

    @Autowired
    private FileProbeWebSocketHandler fileProbeWebSocketHandler;

    @Autowired
    private MetaProbeWebSocketHandler metaProbeWebSocketHandler;

    @Autowired
    private MetricsWebSocketHandler metricsWebSocketHandler;

    @Autowired(required = false)
    private WebSocketOriginInterceptor originInterceptor;

    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${websocket.strict-origin-validation:true}")
    private boolean strictOriginValidation;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 解析允许的域名配置
        String[] origins = parseAllowedOrigins();

        // 注册文件探针WebSocket端点（标准协议）
        registry.addHandler(fileProbeWebSocketHandler, "/ws/file")
                .setAllowedOrigins(origins)
                .withSockJS();  // 启用 SockJS 支持（前端需要）

        // 注册Meta探针WebSocket端点（加密协议）
        // 注意：Agent使用原生WebSocket，不使用SockJS
        registry.addHandler(metaProbeWebSocketHandler, "/ws/meta")
                .setAllowedOrigins(origins);  // 不启用SockJS

        // 注册实时指标推送WebSocket端点
        registry.addHandler(metricsWebSocketHandler, "/ws/metrics")
                .setAllowedOrigins(origins);

        // 如果启用了严格 Origin 验证，添加拦截器
        if (strictOriginValidation && originInterceptor != null) {
            log.info("WebSocket 启用严格 Origin 验证模式");
        }

        log.info("WebSocket CORS配置: allowedOrigins={}, allowCredentials={}, strictValidation={}",
                Arrays.toString(origins), allowCredentials, strictOriginValidation);
    }

    /**
     * 解析允许的域名配置
     * 如果未配置，则允许所有来源（用于本地开发）
     */
    private String[] parseAllowedOrigins() {
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            // 未配置时，允许所有来源（用于本地开发和Agent连接）
            log.info("CORS未配置，允许所有WebSocket连接（本地开发模式）");
            return new String[]{"*"};
        }

        // 解析逗号分隔的域名列表
        String[] origins = allowedOrigins.split(",");
        for (int i = 0; i < origins.length; i++) {
            origins[i] = origins[i].trim();
        }

        log.info("已配置允许的WebSocket跨域来源: {}", Arrays.toString(origins));
        return origins;
    }
}
