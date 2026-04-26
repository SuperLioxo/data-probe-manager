package com.lixin.probe.agent.discovery;


import java.util.List;

/**
 * 发现响应消息 - 服务端响应Agent发现请求的消息
 */
public class DiscoveryResponse {
    /**
     * 消息类型 - 固定为 DISCOVERY_RESPONSE
     */
    private String messageType;

    /**
     * 服务端唯一标识
     */
    private String serverId;

    /**
     * Admin服务的基础URL（不含路径）
     * Agent根据探针类型自行拼接：adminBaseUrl + "/ws/meta" 或 "/ws/file"
     */
    private String adminBaseUrl;

    /**
     * @deprecated 使用 adminBaseUrl 代替
     * WebSocket连接URL（保留用于兼容性）
     */
    @Deprecated
    private String websocketUrl;

    /**
     * 自动创建或已存在的探针列表
     */
    private List<ProbeInfo> probes;

    /**
     * 响应状态码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 探针信息
     */
    public static class ProbeInfo {
        /**
         * 探针ID
         */
        private Long id;

        /**
         * 探针Key
         */
        private String probeKey;

        /**
         * 探针名称
         */
        private String name;

        /**
         * 探针类型
         */
        private String type;

        /**
         * 探针状态
         */
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProbeKey() { return probeKey; }
        public void setProbeKey(String probeKey) { this.probeKey = probeKey; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public String getMessageType() {
        return messageType;
    }
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    public String getServerId() {
        return serverId;
    }
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    public String getAdminBaseUrl() {
        return adminBaseUrl;
    }
    public void setAdminBaseUrl(String adminBaseUrl) {
        this.adminBaseUrl = adminBaseUrl;
    }
    public String getWebsocketUrl() {
        // 兼容旧代码：如果websocketUrl为空，使用adminBaseUrl
        return websocketUrl != null ? websocketUrl : adminBaseUrl;
    }
    public void setWebsocketUrl(String websocketUrl) {
        this.websocketUrl = websocketUrl;
    }
    public List<ProbeInfo> getProbes() {
        return probes;
    }
    public void setProbes(List<ProbeInfo> probes) {
        this.probes = probes;
    }
    public Integer getCode() {
        return code;
    }
    public void setCode(Integer code) {
        this.code = code;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
