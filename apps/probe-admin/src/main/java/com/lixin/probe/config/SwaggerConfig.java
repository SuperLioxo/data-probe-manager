package com.lixin.probe.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI配置（增强版）
 * 提供完整的API文档生成和展示
 *
 * @author Claude Code
 * @date 2026-04-12
 * @version 2.0
 */
@Configuration
public class SwaggerConfig {

    /**
     * OpenAPI文档配置
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("数据探针管理系统 API")
                        .description("基于Spring Boot 3.4的分布式探针管理平台")
                        .version("v2.0.0")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8081")
                                .description("开发环境"),
                        new Server()
                                .url("https://api.production.com")
                                .description("生产环境")
                ));
    }

    /**
     * 探针管理API分组
     */
    @Bean
    public GroupedOpenApi probeApi() {
        return GroupedOpenApi.builder()
                .group("01-探针管理")
                .pathsToMatch("/api/probes/**", "/api/probe-control/**")
                .build();
    }

    /**
     * 监控数据API分组
     */
    @Bean
    public GroupedOpenApi metricsApi() {
        return GroupedOpenApi.builder()
                .group("02-监控数据")
                .pathsToMatch("/api/metrics/**")
                .build();
    }

    /**
     * 认证授权API分组
     */
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("04-认证授权")
                .pathsToMatch("/api/auth/**", "/api/users/**", "/api/permissions/**")
                .build();
    }

    /**
     * 文件管理API分组
     */
    @Bean
    public GroupedOpenApi fileApi() {
        return GroupedOpenApi.builder()
                .group("05-文件管理")
                .pathsToMatch("/api/file-probes/**", "/api/file-metadata/**")
                .build();
    }

    /**
     * 数据库管理API分组
     */
    @Bean
    public GroupedOpenApi databaseApi() {
        return GroupedOpenApi.builder()
                .group("06-数据库管理")
                .pathsToMatch("/api/database-probes/**", "/api/database-connections/**")
                .build();
    }

    /**
     * Agent管理API分组
     */
    @Bean
    public GroupedOpenApi agentApi() {
        return GroupedOpenApi.builder()
                .group("07-Agent管理")
                .pathsToMatch("/api/agents/**")
                .build();
    }

    /**
     * 系统管理API分组
     */
    @Bean
    public GroupedOpenApi systemApi() {
        return GroupedOpenApi.builder()
                .group("08-系统管理")
                .pathsToMatch(
                        "/api/audit-logs/**",
                        "/api/statistics/**",
                        "/api/settings/**",
                        "/api/system-resources/**"
                )
                .build();
    }
}
