package com.lixin.probe.config;

import com.lixin.probe.decorator.CachingProbeServiceDecorator;
import com.lixin.probe.decorator.LoggingProbeServiceDecorator;
import com.lixin.probe.decorator.MonitoringProbeServiceDecorator;
import com.lixin.probe.service.ProbeService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 探针服务装饰器配置
 *
 * <p>根据配置自动装配装饰器，形成装饰器链：
 * <pre>
 * Caching → Logging → Monitoring → ProbeServiceImpl
 * </pre></p>
 *
 * <p>配置项（application.yml）：
 * <pre>
 * probe:
 *   decorator:
 *     cache:
 *       enabled: true    # 是否启用缓存装饰器
 *     logging:
 *       enabled: true    # 是否启用日志装饰器
 *     monitoring:
 *       enabled: true    # 是否启用监控装饰器
 * </pre></p>
 *
 * <p>环境变量覆盖：
 * <pre>
 * PROBE_DECORATOR_CACHE_ENABLED=true
 * PROBE_DECORATOR_LOGGING_ENABLED=true
 * PROBE_DECORATOR_MONITORING_ENABLED=true
 * </pre></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 2.0 (外部化配置)
 */
@Configuration
public class DecoratorConfig {

    @Autowired
    private DecoratorProperties decoratorProperties;

    /**
     * 创建装饰后的探针服务
     *
     * <p>装饰器顺序：
     * 1. Monitoring（最外层） - 先记录监控指标
     * 2. Logging（中间层） - 再记录日志
     * 3. Caching（内层） - 最后处理缓存
     * 4. 原始Service（核心） - 实际业务逻辑</p>
     *
     * @param probeService 原始探针服务
     * @param meterRegistry Micrometer注册表
     * @param cacheManager 缓存管理器
     * @return 装饰后的服务
     */
    @Bean
    @Primary
    public ProbeService decoratedProbeService(
            ProbeService probeService,
            MeterRegistry meterRegistry,
            CacheManager cacheManager) {

        ProbeService decorated = probeService;

        // 1. 缓存装饰器（最内层）
        if (decoratorProperties.getCache().isEnabled()) {
            decorated = new CachingProbeServiceDecorator(decorated, cacheManager);
        }

        // 2. 日志装饰器（中间层）
        if (decoratorProperties.getLogging().isEnabled()) {
            decorated = new LoggingProbeServiceDecorator(decorated);
        }

        // 3. 监控装饰器（最外层）
        if (decoratorProperties.getMonitoring().isEnabled()) {
            decorated = new MonitoringProbeServiceDecorator(decorated, meterRegistry);
        }

        return decorated;
    }
}
