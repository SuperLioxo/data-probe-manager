package com.lixin.probe.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * 探针系统健康检查指示器
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 1.0
 */
@Component
public class ProbeSystemHealthIndicator implements HealthIndicator {

    private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeSystemHealthIndicator.class);

    private final RedisConnectionFactory redisConnectionFactory;

    public ProbeSystemHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        try {
            // 检查Redis连接
            boolean redisHealthy = checkRedis();
            if (redisHealthy) {
                builder.withDetail("redis", "UP")
                       .withDetail("redis_status", "Connected");
            } else {
                builder.down().withDetail("redis", "DOWN")
                              .withDetail("redis_status", "Disconnected");
            }

            // 可以添加更多检查：
            // - 数据库连接
            // - WebSocket连接数
            // - 活跃探针数
            // - 系统负载等

            builder.withDetail("system", "Operational")
                   .withDetail("timestamp", System.currentTimeMillis());

        } catch (Exception e) {
            log.error("健康检查失败", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }

        return builder.build();
    }

    /**
     * 检查Redis连接
     */
    private boolean checkRedis() {
        try {
            if (redisConnectionFactory != null) {
                var connection = redisConnectionFactory.getConnection();
                String pingResult = connection.ping();
                connection.close();
                return "PONG".equals(pingResult);
            }
            return false;
        } catch (Exception e) {
            log.warn("Redis健康检查失败", e);
            return false;
        }
    }
}
