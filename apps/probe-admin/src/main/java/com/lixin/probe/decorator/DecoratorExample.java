package com.lixin.probe.decorator;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.ProbeService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 探针服务装饰器使用示例
 *
 * <p>演示如何手动组装和使用装饰器链。</p>
 *
 * <p>通常情况下，装饰器会通过DecoratorConfig自动装配。
 * 此类仅用于演示装饰器的手动组装方式。</p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
public class DecoratorExample {

    private static final Logger log = LoggerFactory.getLogger(DecoratorExample.class);

    /**
     * 原始探针服务（未装饰）
     */
    @Autowired
    @Qualifier("probeServiceImpl")
    private ProbeService originalProbeService;

    /**
     * 缓存管理器
     */
    @Autowired(required = false)
    private CacheManager cacheManager;

    /**
     * Micrometer注册表
     */
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    /**
     * 手动组装装饰器链
     *
     * <p>装饰器顺序：监控 → 日志 → 缓存 → 原始服务</p>
     *
     * @return 装饰后的服务
     */
    public ProbeService createDecoratedService() {
        ProbeService decorated = originalProbeService;

        // 1. 添加缓存装饰器
        if (cacheManager != null) {
            decorated = new CachingProbeServiceDecorator(decorated, cacheManager);
            log.info("已添加缓存装饰器");
        }

        // 2. 添加日志装饰器
        decorated = new LoggingProbeServiceDecorator(decorated);
        log.info("已添加日志装饰器");

        // 3. 添加监控装饰器
        if (meterRegistry != null) {
            decorated = new MonitoringProbeServiceDecorator(decorated, meterRegistry);
            log.info("已添加监控装饰器");
        }

        return decorated;
    }

    /**
     * 使用装饰器服务的示例
     */
    public void exampleUsage() {
        // 创建装饰后的服务
        ProbeService service = createDecoratedService();

        // 查询探针（会经过缓存、日志、监控三层装饰）
        Probe probe = service.getByProbeKey("test-probe");
        log.info("查询结果: {}", probe);

        // 分页查询（会经过装饰）
        Page<Probe> page = service.getPage(1, 10);
        log.info("分页结果: total={}", page.getTotal());

        // 创建探针（会触发缓存清除、日志记录、监控指标）
        Probe newProbe = Probe.builder()
                .probeKey("new-probe")
                .name("新探针")
                .type("SYSTEM")
                .build();
        service.create(newProbe);
    }

    /**
     * 只使用部分装饰器的示例
     */
    public void partialDecoratorsExample() {
        // 只添加日志装饰器
        ProbeService loggingOnly = new LoggingProbeServiceDecorator(originalProbeService);

        // 只添加缓存装饰器
        ProbeService cachingOnly = new CachingProbeServiceDecorator(
                originalProbeService, cacheManager);

        // 只添加监控装饰器
        ProbeService monitoringOnly = new MonitoringProbeServiceDecorator(
                originalProbeService, meterRegistry);

        // 自定义组合：日志 + 缓存
        ProbeService custom = new CachingProbeServiceDecorator(
                new LoggingProbeServiceDecorator(originalProbeService),
                cacheManager);

        log.info("自定义装饰器组合已创建");
    }

    /**
     * 监控指标查询示例
     */
    public void monitoringMetricsExample() {
        if (meterRegistry == null) {
            log.warn("MeterRegistry未配置，无法查询监控指标");
            return;
        }

        MonitoringProbeServiceDecorator monitoringDecorator =
                new MonitoringProbeServiceDecorator(originalProbeService, meterRegistry);

        // 查询方法调用次数
        double calls = monitoringDecorator.getMethodCallCount("getById");
        log.info("getById调用次数: {}", calls);

        // 查询平均执行时间
        double avgDuration = monitoringDecorator.getMethodAverageDuration("getById");
        log.info("getById平均执行时间: {}ms", avgDuration);
    }
}
