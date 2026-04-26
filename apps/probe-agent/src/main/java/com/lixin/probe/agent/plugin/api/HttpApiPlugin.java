package com.lixin.probe.agent.plugin.api;

import java.util.List;
import java.util.Map;

/**
 * HTTP API 数据源插件 SPI 接口
 */
public interface HttpApiPlugin {

    String getPluginId();

    String getName();

    String getVersion();

    String getDescription();

    /**
     * 测试 API 连接
     */
    boolean testConnection(Map<String, Object> config);

    /**
     * 调用 API 获取数据，返回解析后的行列表
     */
    List<Map<String, Object>> fetchData(Map<String, Object> config);

    /**
     * 获取 API 元数据（字段列表等）
     */
    Map<String, Object> getMetadata(Map<String, Object> config);
}
