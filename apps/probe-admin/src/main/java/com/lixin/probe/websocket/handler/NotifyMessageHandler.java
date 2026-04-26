package com.lixin.probe.websocket.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * 通知消息处理器
 * 处理类型为NOTIFY的消息
 */
@Component
public class NotifyMessageHandler implements MessageHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotifyMessageHandler.class);

    @Override
    public boolean canHandle(String type, String cmd) {
        return "NOTIFY".equals(type);
    }

    @Override
    public void handle(WebSocketSession session, String probeKey, String type, String cmd, Object payload) throws Exception {
        try {
            if (!(payload instanceof Map)) {
                log.warn("通知数据格式错误");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload;

            // 处理通知消息
            String notificationType = (String) data.get("notificationType");
            String message = (String) data.get("message");

            log.info("收到探针通知: probeKey={}, type={}, message={}",
                probeKey, notificationType, message);

            // 根据通知类型进行不同的处理
            if ("ERROR".equals(notificationType)) {
                log.error("探针错误通知: probeKey={}, message={}", probeKey, message);
            } else if ("WARNING".equals(notificationType)) {
                log.warn("探针警告通知: probeKey={}, message={}", probeKey, message);
            } else if ("INFO".equals(notificationType)) {
                log.info("探针信息通知: probeKey={}, message={}", probeKey, message);
            }

        } catch (Exception e) {
            log.error("处理通知消息失败: probeKey={}", probeKey, e);
            throw e;
        }
    }

    @Override
    public String getHandlerName() {
        return "NotifyMessageHandler";
    }
}
