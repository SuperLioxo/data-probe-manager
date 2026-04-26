package com.lixin.probe.agent.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate配置
 * <p>
 * 配置说明：
 * - 连接超时：5秒 - 适用于快速失败，避免长时间等待不可用的后端
 * - 读取超时：10秒 - 给予足够的时间让后端处理请求
 * 这些超时设置配合健康检查和重试机制，可以提供良好的容错能力
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}
