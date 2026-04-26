package com.lixin.probe.dto;

import com.lixin.probe.entity.Probe;
import lombok.Data;

import java.util.List;

/**
 * 发现响应消息 - 服务端响应Agent发现请求的消息
 */
@Data
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
     * Admin服务的基础URL（不包含路径）
     * Agent根据探针类型自行拼接路径
     */
    private String adminBaseUrl;

    /**
     * 自动创建或已存在的探针列表
     */
    private List<Probe> probes;

    /**
     * 响应状态码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;
}
