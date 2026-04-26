package com.lixin.probe.agent.pojo;

import com.lixin.probe.agent.constant.Command;

/**
 * WebSocket 消息对象
 */
public class WebSocketMessage<T> {

    /**
     * 消息ID
     */
    private Long id;

    /**
     * 消息类型: REQUEST/RESPONSE/NOTIFY
     */
    private String type;

    /**
     * 命令类型
     */
    private String cmd;

    /**
     * 探针编码
     */
    private String code;

    /**
     * 认证密钥
     */
    private String key;

    /**
     * 消息载荷
     */
    private T payload;

    /**
     * 消息类型枚举
     */
    public enum Type {
        REQUEST,
        RESPONSE,
        NOTIFY
    }

    /**
     * 判断是否为请求消息
     */
    public boolean isRequest() {
        return Type.REQUEST.name().equals(type);
    }

    /**
     * 判断是否为响应消息
     */
    public boolean isResponse() {
        return Type.RESPONSE.name().equals(type);
    }

    /**
     * 判断是否为通知消息
     */
    public boolean isNotify() {
        return Type.NOTIFY.name().equals(type);
    }

    /**
     * 获取命令枚举
     */
    public Command getCommand() {
        if (cmd == null) {
            return null;
        }
        try {
            return Command.valueOf(cmd);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getCmd() {
        return cmd;
    }
    public void setCmd(String cmd) {
        this.cmd = cmd;
    }
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public T getPayload() {
        return payload;
    }
    public void setPayload(T payload) {
        this.payload = payload;
    }

    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    public static class Builder<T> {
        private WebSocketMessage<T> message = new WebSocketMessage<T>();

        public Builder<T> id(Long id) {
            message.id = id;
            return this;
        }

        public Builder<T> type(String type) {
            message.type = type;
            return this;
        }

        public Builder<T> cmd(String cmd) {
            message.cmd = cmd;
            return this;
        }

        public Builder<T> code(String code) {
            message.code = code;
            return this;
        }

        public Builder<T> key(String key) {
            message.key = key;
            return this;
        }

        public Builder<T> payload(T payload) {
            message.payload = payload;
            return this;
        }

        public WebSocketMessage<T> build() {
            return message;
        }
    }
}
