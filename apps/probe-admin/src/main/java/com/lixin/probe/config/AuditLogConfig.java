package com.lixin.probe.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 审计日志配置类
 */
@Configuration
@EnableConfigurationProperties(AuditLogProperties.class)
@EnableAspectJAutoProxy
@EnableScheduling
public class AuditLogConfig {

    /**
     * 审计日志配置会在启动时自动加载
     * 通过AuditLogProperties类可以访问所有配置项
     */
}
