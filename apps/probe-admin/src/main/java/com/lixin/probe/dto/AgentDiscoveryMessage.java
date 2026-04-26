package com.lixin.probe.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent发现消息 - Agent通过UDP广播发送的发现消息
 */
@Data
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
    @Data
    public static class ProbeCapability {
        /**
         * 探针类型（SYSTEM, DATABASE, FILE, UNIFIED）
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
    }
}
