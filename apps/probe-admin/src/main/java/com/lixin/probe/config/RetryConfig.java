package com.lixin.probe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 重试机制配置
 *
 * @author probe-admin
 * @since 1.0.0
 */
@Configuration
@EnableRetry
public class RetryConfig {
    // 启用重试机制，允许在Service方法上使用@Retryable注解
}
