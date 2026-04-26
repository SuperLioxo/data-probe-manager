package com.lixin.probe.websocket.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

/**
 * WebSocket消息分发器
 * 负责将接收到的消息分发给对应的处理器
 *
 * @author Claude Code
 * @date 2026-03-11
 */
@Service
public class MessageDispatcher {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MessageDispatcher.class);

    private final List<MessageHandler> handlers;

    /**
     * 构造函数，自动注入所有MessageHandler实现
     */
    @Autowired
    public MessageDispatcher(List<MessageHandler> handlers) {
        this.handlers = handlers;
        log.info("消息分发器初始化完成，加载了{}个消息处理器", handlers.size());

        // 打印所有已注册的处理器
        for (MessageHandler handler : handlers) {
            log.info("  - {}", handler.getHandlerName());
        }
    }

    /**
     * 分发消息到对应的处理器
     *
     * @param session  WebSocket会话
     * @param probeKey 探针标识
     * @param type     消息类型
     * @param cmd      消息命令
     * @param payload  消息负载
     * @throws Exception 如果处理失败或找不到合适的处理器
     */
    public void dispatch(WebSocketSession session, String probeKey, String type, String cmd, Object payload) throws Exception {
        if (handlers == null || handlers.isEmpty()) {
            log.error("没有可用的消息处理器");
            throw new IllegalStateException("消息处理器未初始化");
        }

        // 查找可以处理该消息的处理器
        MessageHandler handler = findHandler(type, cmd);

        if (handler == null) {
            log.warn("未找到合适的消息处理器: type={}, cmd={}", type, cmd);
            throw new IllegalArgumentException("未知的消息类型或命令: type=" + type + ", cmd=" + cmd);
        }

        // 委托给对应的处理器处理
        log.debug("分发消息到处理器: handler={}, type={}, cmd={}, probeKey={}",
            handler.getHandlerName(), type, cmd, probeKey);

        try {
            handler.handle(session, probeKey, type, cmd, payload);
        } catch (Exception e) {
            log.error("消息处理失败: handler={}, type={}, cmd={}, probeKey={}",
                handler.getHandlerName(), type, cmd, probeKey, e);
            throw e;
        }
    }

    /**
     * 查找可以处理该消息的处理器
     *
     * @param type 消息类型
     * @param cmd  消息命令
     * @return 找到的处理器，如果没有找到返回null
     */
    private MessageHandler findHandler(String type, String cmd) {
        for (MessageHandler handler : handlers) {
            if (handler.canHandle(type, cmd)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * 获取所有已注册的处理器
     *
     * @return 处理器列表
     */
    public List<MessageHandler> getHandlers() {
        return List.copyOf(handlers);
    }
}
