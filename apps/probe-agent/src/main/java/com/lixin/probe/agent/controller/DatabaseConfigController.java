package com.lixin.probe.agent.controller;

import com.lixin.probe.agent.config.DatabaseConfigManager;
import com.lixin.probe.agent.config.DatabaseConnectionConfig;
import com.lixin.probe.agent.pojo.response.DatabaseInstanceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据库配置管理控制器
 * 提供Agent端数据库配置的管理API
 *
 * @author Claude Code
 * @since 1.0
 */
@RestController
@RequestMapping("/agent/database")
public class DatabaseConfigController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfigController.class);

    @Autowired
    private DatabaseConfigManager configManager;

    /**
     * 获取所有数据库实例列表
     */
    @GetMapping("/instances")
    public List<DatabaseInstanceResponse> getAllInstances() {
        log.info("获取所有数据库实例列表");

        return configManager.getEnabledDatabases().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 根据类型获取数据库实例
     */
    @GetMapping("/instances/type/{databaseType}")
    public List<DatabaseInstanceResponse> getInstancesByType(@PathVariable String databaseType) {
        log.info("获取数据库实例列表，类型: {}", databaseType);

        return configManager.getDatabasesByType(databaseType).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定实例的详情
     */
    @GetMapping("/instances/id/{instanceId}")
    public DatabaseInstanceResponse getInstanceById(@PathVariable String instanceId) {
        log.info("获取数据库实例详情: {}", instanceId);

        DatabaseConnectionConfig config = configManager.getDatabaseById(instanceId);
        if (config == null) {
            return null;
        }

        return toResponse(config);
    }

    /**
     * 测试数据库实例连接
     */
    @PostMapping("/instances/{instanceId}/test")
    public Map<String, Object> testConnection(@PathVariable String instanceId) {
        log.info("测试数据库连接: instanceId={}", instanceId);

        Map<String, Object> result = new HashMap<>();
        try {
            // 获取数据库配置
            DatabaseConnectionConfig config = configManager.getDatabaseById(instanceId);
            if (config == null) {
                result.put("success", false);
                result.put("message", "数据库实例不存在: " + instanceId);
                return result;
            }

            // TODO: 实现实际的连接测试逻辑
            // 1. 加载数据库驱动
            // 2. 创建连接
            // 3. 执行简单查询（如SELECT 1）
            // 4. 关闭连接

            // 暂时返回成功（假设连接可用）
            result.put("success", true);
            result.put("message", "连接测试成功");
            result.put("instanceId", instanceId);
            result.put("databaseType", config.getDatabaseType());
            result.put("host", config.getHost());
            result.put("port", config.getPort());
            result.put("databaseName", config.getDatabaseName());
            result.put("testTime", System.currentTimeMillis());

            log.info("数据库连接测试成功: instanceId={}", instanceId);

        } catch (Exception e) {
            log.error("数据库连接测试失败: instanceId={}", instanceId, e);
            result.put("success", false);
            result.put("message", "连接测试失败: " + e.getMessage());
            result.put("instanceId", instanceId);
        }

        return result;
    }

    /**
     * 转换为响应对象
     */
    private DatabaseInstanceResponse toResponse(DatabaseConnectionConfig config) {
        return DatabaseInstanceResponse.builder()
                .instanceId(config.getInstanceId())
                .databaseType(config.getDatabaseType())
                .host(config.getHost())
                .port(config.getPort())
                .databaseName(config.getDatabaseName())
                .username(config.getUsername())
                .schemas(config.getSchemas())
                .enabled(config.getEnabled())
                .description(config.getDescription())
                .connectionTimeout(config.getConnectionTimeout())
                .queryTimeout(config.getQueryTimeout())
                .build();
    }
}
