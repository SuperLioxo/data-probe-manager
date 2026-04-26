package com.lixin.probe.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 性能监控配置（增强版）
 * 集成Micrometer进行全面的性能指标收集
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0
 */
@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
public class MetricsConfig {

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * JVM内存指标
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     * JVM线程指标
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    /**
     * 处理器指标
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    /**
     * 运行时间指标
     */
    @Bean
    public UptimeMetrics uptimeMetrics() {
        return new UptimeMetrics();
    }

    /**
     * 记录API调用耗时
     */
    public void recordApiCall(String apiName, String method, long durationMs) {
        Timer.builder("api.calls")
                .tag("api", apiName)
                .tag("method", method)
                .description("API调用耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录业务操作耗时
     */
    public void recordBusinessOperation(String operation, String module, long durationMs) {
        Timer.builder("business.operation")
                .tag("operation", operation)
                .tag("module", module)
                .description("业务操作耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录数据库查询耗时
     */
    public void recordDatabaseQuery(String queryType, String table, long durationMs) {
        Timer.builder("database.query")
                .tag("type", queryType)
                .tag("table", table)
                .description("数据库查询耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        // 慢查询告警
        if (durationMs > 1000) {
            meterRegistry.counter("database.slow.query",
                    "type", queryType,
                    "table", table
            ).increment();
        }
    }

    /**
     * 记录缓存命中/未命中
     */
    public void recordCacheHit(String cacheName, boolean hit) {
        meterRegistry.counter("cache.access",
                "cache", cacheName,
                "result", hit ? "hit" : "miss"
        ).increment();
    }

    /**
     * 记录WebSocket连接数
     */
    public void recordWebSocketConnections(int count) {
        meterRegistry.gauge("websocket.connections", count);
    }

    /**
     * 记录活跃探针数
     */
    public void recordActiveProbes(int count) {
        meterRegistry.gauge("probe.active", count);
    }

    /**
     * 记录告警数量
     */
    public void recordAlertTriggered(String severity) {
        meterRegistry.counter("alert.triggered",
                "severity", severity
        ).increment();
    }

    /**
     * 记录HTTP请求错误
     */
    public void recordHttpError(int statusCode, String uri) {
        meterRegistry.counter("http.errors",
                "status", String.valueOf(statusCode),
                "uri", uri
        ).increment();
    }

    /**
     * 记录N+1查询问题
     */
    public void recordNPlusOneQuery(String method) {
        meterRegistry.counter("database.n_plus_one",
                "method", method
        ).increment();
    }

    /**
     * 记录数据导出操作
     */
    public void recordDataExport(String type, int rowCount, long durationMs) {
        Timer.builder("data.export")
                .tag("type", type)
                .description("数据导出耗时")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        // 记录导出行数
        meterRegistry.counter("data.export.rows",
                "type", type
        ).increment(rowCount);
    }

    /**
     * 自定义Timer构建器
     */
    public static Timer.Builder timerBuilder(String name, String description) {
        return Timer.builder(name)
                .description(description)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .sla(Duration.ofMillis(100),
                    Duration.ofMillis(200),
                    Duration.ofMillis(500),
                    Duration.ofMillis(1000),
                    Duration.ofMillis(2000));
    }
}
