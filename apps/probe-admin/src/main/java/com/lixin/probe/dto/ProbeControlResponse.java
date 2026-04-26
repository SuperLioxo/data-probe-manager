package com.lixin.probe.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 探针控制响应DTO
 */
@Data
@Builder
public class ProbeControlResponse {
    /**
     * 命令ID
     */
    private String commandId;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private Map<String, Object> data;

    /**
     * 创建错误响应
     */
    public static ProbeControlResponse error(String message) {
        return ProbeControlResponse.builder()
                .success(false)
                .message(message)
                .build();
    }

    /**
     * 创建成功响应
     */
    public static ProbeControlResponse success(Map<String, Object> data) {
        return ProbeControlResponse.builder()
                .success(true)
                .message("执行成功")
                .data(data)
                .build();
    }

    /**
     * 创建成功响应（带消息）
     */
    public static ProbeControlResponse success(String message, Map<String, Object> data) {
        return ProbeControlResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }
}
