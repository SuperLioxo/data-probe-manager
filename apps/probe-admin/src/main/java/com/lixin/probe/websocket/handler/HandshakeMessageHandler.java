package com.lixin.probe.websocket.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * 握手消息处理器
 * 处理Agent的握手请求
 *
 * @author Claude Code
 * @date 2026-03-21
 */
@Component
public class HandshakeMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(HandshakeMessageHandler.class);

    @Override
    public boolean canHandle(String type, String cmd) {
        // 处理 REQUEST 类型的握手命令
        return "REQUEST".equals(type) && "HANDSHAKE".equals(cmd);
    }

    @Override
    public void handle(WebSocketSession session, String probeKey, String type, String cmd, Object payload) throws Exception {
        try {
            log.info("收到Agent握手消息: probeKey={}", probeKey);

            // 握手成功，可以在这里做一些初始化工作
            // 例如记录连接时间、验证Agent身份等

            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) payload;
                log.debug("握手数据: {}", data);
            }

        } catch (Exception e) {
            log.error("处理握手消息失败: probeKey={}", probeKey, e);
            throw e;
        }
    }

    @Override
    public String getHandlerName() {
        return "HandshakeMessageHandler";
    }
}
