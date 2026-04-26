package com.lixin.probe.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * InfluxDB配置类
 *
 * <p>提供InfluxDB客户端的配置和初始化，支持连接池、重试等高级配置。</p>
 *
 * <p>配置示例：</p>
 * <pre>
 * influx:
 *   url: http://localhost:8086
 *   token: your-token
 *   org: probe-org
 *   bucket: probe-metrics
 *   enabled: true
 *   write-options:
 *     batch-size: 1000
 *     flush-interval: 1000
 *     jitter-interval: 0
 *     buffer-limit: 10000
 *   retry-options:
 *     max-retries: 3
 *     initial-interval: 1000
 *     max-interval: 10000
 *     multiplier: 2.0
 * </pre>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Configuration
@ConditionalOnProperty(name = "influx.enabled", havingValue = "true", matchIfMissing = false)
@ConfigurationProperties(prefix = "influx")
public class InfluxDBConfig {

    private static final Logger log = LoggerFactory.getLogger(InfluxDBConfig.class);

    /**
     * InfluxDB服务器URL
     */
    private String url;

    /**
     * 认证令牌
     */
    private String token;

    /**
     * 组织名称
     */
    private String org;

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * 是否启用InfluxDB
     */
    private boolean enabled = true;

    /**
     * 写入选项配置
     */
    private WriteOptions writeOptions = new WriteOptions();

    /**
     * 重试选项配置
     */
    private RetryOptions retryOptions = new RetryOptions();

    private InfluxDBClient influxDBClient;

    @Bean(destroyMethod = "close")
    public InfluxDBClient influxDBClient() {
        try {
            log.info("初始化InfluxDB客户端: url={}, org={}, bucket={}", url, org, bucket);

            influxDBClient = InfluxDBClientFactory.create(
                url,
                token.toCharArray(),
                org,
                bucket
            );

            // 验证连接
            try {
                var health = influxDBClient.health();
                if (health.getStatus().toString().equals("pass")) {
                    log.info("InfluxDB连接验证成功");
                } else {
                    log.warn("InfluxDB健康状态: {}", health.getStatus());
                }
            } catch (Exception e) {
                log.error("InfluxDB连接验证失败", e);
                throw e;
            }

            return influxDBClient;

        } catch (Exception e) {
            log.error("创建InfluxDB客户端失败", e);
            throw new RuntimeException("Failed to create InfluxDB client", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (influxDBClient != null) {
            try {
                influxDBClient.close();
                log.info("InfluxDB客户端已关闭");
            } catch (Exception e) {
                log.error("关闭InfluxDB客户端失败", e);
            }
        }
    }

    // Getters and Setters

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public WriteOptions getWriteOptions() {
        return writeOptions;
    }

    public void setWriteOptions(WriteOptions writeOptions) {
        this.writeOptions = writeOptions;
    }

    public RetryOptions getRetryOptions() {
        return retryOptions;
    }

    public void setRetryOptions(RetryOptions retryOptions) {
        this.retryOptions = retryOptions;
    }

    /**
     * 写入选项配置
     */
    public static class WriteOptions {
        /**
         * 批量写入大小（条数）
         */
        private int batchSize = 1000;

        /**
         * 刷新间隔（毫秒）
         */
        private int flushInterval = 1000;

        /**
         * 抖动间隔（毫秒）
         */
        private int jitterInterval = 0;

        /**
         * 缓冲区限制（条数）
         */
        private int bufferLimit = 10000;

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getFlushInterval() {
            return flushInterval;
        }

        public void setFlushInterval(int flushInterval) {
            this.flushInterval = flushInterval;
        }

        public int getJitterInterval() {
            return jitterInterval;
        }

        public void setJitterInterval(int jitterInterval) {
            this.jitterInterval = jitterInterval;
        }

        public int getBufferLimit() {
            return bufferLimit;
        }

        public void setBufferLimit(int bufferLimit) {
            this.bufferLimit = bufferLimit;
        }
    }

    /**
     * 重试选项配置
     */
    public static class RetryOptions {
        /**
         * 最大重试次数
         */
        private int maxRetries = 3;

        /**
         * 初始重试间隔（毫秒）
         */
        private long initialInterval = 1000;

        /**
         * 最大重试间隔（毫秒）
         */
        private long maxInterval = 10000;

        /**
         * 间隔乘数
         */
        private double multiplier = 2.0;

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getInitialInterval() {
            return initialInterval;
        }

        public void setInitialInterval(long initialInterval) {
            this.initialInterval = initialInterval;
        }

        public long getMaxInterval() {
            return maxInterval;
        }

        public void setMaxInterval(long maxInterval) {
            this.maxInterval = maxInterval;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }
    }
}
