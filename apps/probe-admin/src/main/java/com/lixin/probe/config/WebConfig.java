package com.lixin.probe.config;

import com.lixin.probe.interceptor.PermissionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置 - 配置拦截器
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired(required = false)
    private com.lixin.probe.config.JwtInterceptor jwtInterceptor;

    @Autowired(required = false)
    private PermissionInterceptor permissionInterceptor;

    @Autowired(required = false)
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 速率限制拦截器（如果存在）
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/api/auth/login");  // 只对登录端点进行速率限制
        }

        // JWT拦截器（如果存在）
        if (jwtInterceptor != null) {
            registry.addInterceptor(jwtInterceptor)
                    .addPathPatterns("/api/**")  // 拦截所有API请求
                    .excludePathPatterns(
                            "/api/auth/login",                   // 登录接口
                            "/api/auth/register",                // 注册接口（如果有）
                            "/api/auth/refresh",                 // 刷新Token接口（使用refresh token验证）
                            "/api/auth/generate-password",      // 临时密码生成接口
                            "/api/agents/*/sync",                // Agent探针同步
                            "/api/agents/*/cdc-events",          // Agent CDC事件上报
                            "/api/agents/*/datasource-heartbeat", // Agent数据源心跳上报
                            "/api/agents/*/quality-rules",         // Agent拉取质量规则
                            "/api/agents/*/quality-bad-records",   // Agent上报坏记录
                            "/api/plugins/**",                   // Agent插件上报相关接口
                            "/api/probes/online",                // 获取在线探针列表
                            "/api/metrics/probe/*",              // 查询探针指标数据
                            "/api/metrics/probe/*/latest",       // 获取探针最新指标数据
                            "/api/metrics/probe/*/summary",      // 获取探针指标摘要
                            "/api/statistics/**",                // 统计数据查询（公开访问）
                            "/api/audit-logs/**",                // 临时开放审计日志API用于测试
                            "/api/database-probes/**",           // 🔓 数据库探针API（临时开放）
                            "/swagger-ui/**",                    // Swagger UI
                            "/v3/api-docs/**",                   // API文档
                            "/swagger-resources/**",             // Swagger资源
                            "/webjars/**",                       // WebJars
                            "/error"                             // 错误页面
                    );
        }

        // 权限拦截器（如果存在）
        if (permissionInterceptor != null) {
            registry.addInterceptor(permissionInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns(
                            "/api/auth/**",                 // 登录注册接口
                            "/api/probe/*/heartbeat",       // 探针心跳
                            "/api/metrics",                 // 探针上报数据（使用探针key验证）
                            "/api/metrics/*",               // 探针上报数据
                            "/api/probes/online",           // 获取在线探针列表
                            "/api/metrics/probe/*",         // 查询探针指标数据
                            "/api/metrics/probe/*/latest",  // 获取探针最新指标数据
                            "/api/metrics/probe/*/summary", // 获取探针指标摘要
                            "/api/statistics/**",           // 统计数据查询（公开访问）
                            "/api/audit-logs/**",           // 临时开放审计日志API用于测试
                            "/api/database-probes/**",       // 🔓 数据库探针API（临时开放）
                            "/swagger-ui/**",               // Swagger UI
                            "/v3/api-docs/**",              // API文档
                            "/swagger-resources/**",        // Swagger资源
                            "/webjars/**",                  // WebJars
                            "/error"                        // 错误页面
                    );
        }
    }
}
