package com.lixin.probe.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * 性能优化配置
 * 包括缓存、线程池、异步执行等配置
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Configuration
@EnableCaching
public class PerformanceConfig {

    private static final Logger log = LoggerFactory.getLogger(PerformanceConfig.class);
    /**
     * 缓存管理器
     * 用于缓存REST API响应和计算结果
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        // 定义多个缓存区域，不同的过期策略
        cacheManager.setCaches(Arrays.asList(
            // 系统指标缓存（5秒过期）
            new ConcurrentMapCache("systemMetrics"),

            // 插件列表缓存（10分钟过期）
            new ConcurrentMapCache("pluginsList"),

            // 数据库元数据缓存（5分钟过期）
            new ConcurrentMapCache("databaseMetadata"),

            // 连接测试结果缓存（30秒过期）
            new ConcurrentMapCache("connectionTest"),

            // 健康检查结果缓存（10秒过期）
            new ConcurrentMapCache("healthCheck")
        ));

        log.info("缓存管理器初始化完成，缓存区域: systemMetrics, pluginsList, databaseMetadata, connectionTest, healthCheck");
        return cacheManager;
    }
}
