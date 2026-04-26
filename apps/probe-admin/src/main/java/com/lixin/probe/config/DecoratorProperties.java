package com.lixin.probe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 探针服务装饰器配置属性
 *
 * <p>从application.yml读取配置：
 * <pre>
 * probe:
 *   decorator:
 *     cache:
 *       enabled: true
 *     logging:
 *       enabled: true
 *     monitoring:
 *       enabled: true
 * </pre></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
@ConfigurationProperties(prefix = "probe.decorator")
public class DecoratorProperties {

    /**
     * 缓存装饰器配置
     */
    private CacheDecorator cache = new CacheDecorator();

    /**
     * 日志装饰器配置
     */
    private LoggingDecorator logging = new LoggingDecorator();

    /**
     * 监控装饰器配置
     */
    private MonitoringDecorator monitoring = new MonitoringDecorator();

    public CacheDecorator getCache() {
        return cache;
    }

    public void setCache(CacheDecorator cache) {
        this.cache = cache;
    }

    public LoggingDecorator getLogging() {
        return logging;
    }

    public void setLogging(LoggingDecorator logging) {
        this.logging = logging;
    }

    public MonitoringDecorator getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(MonitoringDecorator monitoring) {
        this.monitoring = monitoring;
    }

    /**
     * 缓存装饰器配置
     */
    public static class CacheDecorator {
        /**
         * 是否启用缓存装饰器
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 日志装饰器配置
     */
    public static class LoggingDecorator {
        /**
         * 是否启用日志装饰器
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 监控装饰器配置
     */
    public static class MonitoringDecorator {
        /**
         * 是否启用监控装饰器
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
