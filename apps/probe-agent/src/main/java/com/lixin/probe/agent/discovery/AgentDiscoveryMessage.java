package com.lixin.probe.agent.discovery;


import java.util.List;
import java.util.Map;

/**
 * Agent发现消息 - Agent通过UDP广播发送的发现消息
 */
public class AgentDiscoveryMessage {
    /**
     * 消息类型 - 固定为 AGENT_DISCOVERY
     */
    private String messageType;

    /**
     * Agent唯一标识
     */
    private String agentId;

    /**
     * Agent名称
     */
    private String agentName;

    /**
     * Agent主机IP
     */
    private String hostIp;

    /**
     * Agent版本
     */
    private String version;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * Agent UDP监听端口（用于接收服务端响应）
     */
    private Integer discoveryPort;

    /**
     * Agent HTTP服务端口（用于Admin连接到Agent）
     */
    private Integer agentPort;

    /**
     * 探针能力列表
     */
    private List<ProbeCapability> capabilities;

    /**
     * 探针能力
     */
    public static class ProbeCapability {
        /**
         * 探针类型（SYSTEM, DATABASE, FILE）
         * @deprecated 网络探针已并入系统探针，不再使用NETWORK类型
         */
        private String type;

        /**
         * 是否启用
         */
        private Boolean enabled;

        /**
         * 采集间隔（秒）
         */
        private Integer collectInterval;

        /**
         * 扩展配置参数
         */
        private Map<String, Object> config;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Integer getCollectInterval() { return collectInterval; }
        public void setCollectInterval(Integer collectInterval) { this.collectInterval = collectInterval; }
        public Map<String, Object> getConfig() { return config; }
        public void setConfig(Map<String, Object> config) { this.config = config; }
    }

    public String getMessageType() {
        return messageType;
    }
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    public String getAgentId() {
        return agentId;
    }
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
    public String getAgentName() {
        return agentName;
    }
    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
    public String getHostIp() {
        return hostIp;
    }
    public void setHostIp(String hostIp) {
        this.hostIp = hostIp;
    }
    public String getVersion() {
        return version;
    }
    public void setVersion(String version) {
        this.version = version;
    }
    public Long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    public Integer getDiscoveryPort() {
        return discoveryPort;
    }
    public void setDiscoveryPort(Integer discoveryPort) {
        this.discoveryPort = discoveryPort;
    }
    public Integer getAgentPort() {
        return agentPort;
    }
    public void setAgentPort(Integer agentPort) {
        this.agentPort = agentPort;
    }
    public List<ProbeCapability> getCapabilities() {
        return capabilities;
    }
    public void setCapabilities(List<ProbeCapability> capabilities) {
        this.capabilities = capabilities;
    }
}
