package com.lixin.probe.websocket.handler;

import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * WebSocket消息处理器接口
 * 使用策略模式处理不同类型的WebSocket消息
 *
 * @author Claude Code
 * @date 2026-03-11
 */
public interface MessageHandler {

    /**
     * 判断是否可以处理该消息
     *
     * @param type 消息类型
     * @param cmd  消息命令
     * @return true如果可以处理该消息
     */
    boolean canHandle(String type, String cmd);

    /**
     * 处理消息
     *
     * @param session   WebSocket会话
     * @param probeKey  探针标识
     * @param type      消息类型
     * @param cmd       消息命令
     * @param payload   消息负载
     * @throws Exception 处理异常
     */
    void handle(WebSocketSession session, String probeKey, String type, String cmd, Object payload) throws Exception;

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    String getHandlerName();
}
