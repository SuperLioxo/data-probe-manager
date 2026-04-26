package com.lixin.probe.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务指标配置
 * <p>
 * 为关键业务操作添加自定义指标，便于监控和分析。
 * 所有指标都会自动暴露给Prometheus进行采集。
 * </p>
 *
 * <p>主要指标:</p>
 * <ul>
 *   <li>探针创建/更新/删除计数</li>
 *   <li>用户登录/登出计数</li>
 *   <li>数据库查询计时</li>
 *   <li>异常计数</li>
 *   <li>告警触发计数</li>
 * </ul>
 *
 * @author Development Team
 * @date 2026-03-20
 * @version 1.0
 */
@Configuration
public class BusinessMetricsConfig {

    /**
     * 创建探针计数器
     */
    @Bean
    public Counter probeCreatedCounter(MeterRegistry registry) {
        return Counter.builder("probe.created")
                .description("探针创建总数")
                .tag("type", "system")
                .register(registry);
    }

    /**
     * 探针删除计数器
     */
    @Bean
    public Counter probeDeletedCounter(MeterRegistry registry) {
        return Counter.builder("probe.deleted")
                .description("探针删除总数")
                .register(registry);
    }

    /**
     * 用户登录计数器
     */
    @Bean
    public Counter userLoginCounter(MeterRegistry registry) {
        return Counter.builder("user.login")
                .description("用户登录总数")
                .register(registry);
    }

    /**
     * 用户登录失败计数器
     */
    @Bean
    public Counter userLoginFailedCounter(MeterRegistry registry) {
        return Counter.builder("user.login.failed")
                .description("用户登录失败总数")
                .register(registry);
    }

    /**
     * 告警触发计数器
     */
    @Bean
    public Counter alertTriggeredCounter(MeterRegistry registry) {
        return Counter.builder("alert.triggered")
                .description("告警触发总数")
                .register(registry);
    }

    /**
     * 数据库查询计时器
     */
    @Bean
    public Timer databaseQueryTimer(MeterRegistry registry) {
        return Timer.builder("database.query.duration")
                .description("数据库查询耗时")
                .tag("operation", "query")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * HTTP请求计时器（除了Spring Boot自带的）
     */
    @Bean
    public Timer httpRequestTimer(MeterRegistry registry) {
        return Timer.builder("http.server.request.duration")
                .description("HTTP请求处理耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .sla(java.time.Duration.ofMillis(100),
                     java.time.Duration.ofMillis(200),
                     java.time.Duration.ofMillis(500),
                     java.time.Duration.ofSeconds(1))
                .register(registry);
    }

    /**
     * 业务异常计数器
     */
    @Bean
    public Counter businessExceptionCounter(MeterRegistry registry) {
        return Counter.builder("business.exception")
                .description("业务异常总数")
                .register(registry);
    }

    /**
     * 探针心跳超时计数器
     */
    @Bean
    public Counter probeHeartbeatTimeoutCounter(MeterRegistry registry) {
        return Counter.builder("probe.heartbeat.timeout")
                .description("探针心跳超时次数")
                .register(registry);
    }

    /**
     * 探针离线计数器
     */
    @Bean
    public Counter probeOfflineCounter(MeterRegistry registry) {
        return Counter.builder("probe.offline")
                .description("探针离线次数")
                .register(registry);
    }

    /**
     * 文件扫描计时器
     */
    @Bean
    public Timer fileScanTimer(MeterRegistry registry) {
        return Timer.builder("file.scan.duration")
                .description("文件扫描耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * WebSocket连接计数器
     */
    @Bean
    public Counter websocketConnectionCounter(MeterRegistry registry) {
        return Counter.builder("websocket.connection")
                .description("WebSocket连接总数")
                .register(registry);
    }

    /**
     * 当前在线探针数（Gauge）
     */
    @Bean
    public AtomicInteger onlineProbeCount() {
        return new AtomicInteger(0);
    }

    @Bean
    public AtomicInteger activeUserCount() {
        return new AtomicInteger(0);
    }
}