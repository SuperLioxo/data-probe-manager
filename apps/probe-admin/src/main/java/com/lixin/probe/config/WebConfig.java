package com.lixin.probe.config;

import com.lixin.probe.interceptor.PermissionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类 —— 安全拦截器注册中心
 *
 * <p>本类是Spring MVC层面的安全配置核心，负责将各个安全拦截器注册到Spring的拦截器链中，
 * 并配置每个拦截器的拦截路径和排除路径。拦截器的执行顺序取决于注册顺序（先注册先执行）。</p>
 *
 * <h3>拦截器执行链（按注册顺序）：</h3>
 * <pre>
 *   请求进入
 *     │
 *     ▼
 *   ┌─────────────────────────┐
 *   │ 1. RateLimitInterceptor  │  ← 登录接口限流（防暴力破解）
 *   │    仅拦截 /api/auth/login │
 *   └──────────┬──────────────┘
 *              │
 *              ▼
 *   ┌─────────────────────────┐
 *   │ 2. JwtInterceptor        │  ← JWT认证 + 注解级权限校验
 *   │    拦截 /api/**          │
 *   │    排除公开API、Swagger等  │
 *   └──────────┬──────────────┘
 *              │
 *              ▼
 *   ┌─────────────────────────┐
 *   │ 3. PermissionInterceptor  │  ← 细粒度权限校验
 *   │    拦截 /api/**          │
 *   │    排除公开API、Swagger等  │
 *   └──────────┬──────────────┘
 *              │
 *              ▼
 *        Controller 处理请求
 * </pre>
 *
 * <h3>路径分类说明：</h3>
 * <ul>
 *   <li><b>公开路径（Public）</b> —— 无需认证即可访问，如登录、注册、Agent上报等</li>
 *   <li><b>受保护路径（Protected）</b> —— 需要JWT Token认证，如 /api/** 下大部分接口</li>
 *   <li><b>特殊路径</b> —— Agent探针接口使用探针key验证而非JWT，因此排除在JWT拦截之外</li>
 * </ul>
 *
 * <h3>注意事项：</h3>
 * <ul>
 *   <li>所有拦截器使用 @Autowired(required = false) 注入，确保在某些组件未配置时不会导致启动失败</li>
 *   <li>新增公开API时，需要同步更新JwtInterceptor和PermissionInterceptor的排除路径列表</li>
 *   <li>Swagger相关路径始终排除，确保API文档可正常访问</li>
 * </ul>
 *
 * @see JwtInterceptor
 * @see PermissionInterceptor
 * @see RateLimitInterceptor
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * JWT认证拦截器（可选注入）
     * 负责验证请求中的JWT Token，提取用户信息，并检查方法级权限注解
     */
    @Autowired(required = false)
    private com.lixin.probe.config.JwtInterceptor jwtInterceptor;

    /**
     * 权限拦截器（可选注入）
     * 负责基于@RequirePermission注解的细粒度权限校验，
     * 从数据库查询用户权限并比对所需权限
     */
    @Autowired(required = false)
    private PermissionInterceptor permissionInterceptor;

    /**
     * 速率限制拦截器（可选注入）
     * 负责对登录接口进行IP级别的请求频率限制，防止暴力破解密码
     */
    @Autowired(required = false)
    private RateLimitInterceptor rateLimitInterceptor;

    /**
     * 注册拦截器并配置拦截/排除路径
     *
     * <p>拦截器的注册顺序即为执行顺序。每个拦截器通过addPathPatterns指定需要拦截的路径，
     * 通过excludePathPatterns指定不需要拦截（直接放行）的路径。</p>
     *
     * <h3>路径设计原则：</h3>
     * <ul>
     *   <li>以 /api/ 开头的路径默认需要认证，除非明确排除</li>
     *   <li>认证相关接口（登录、注册、刷新Token）必须排除</li>
     *   <li>Agent探针通信接口（同步、心跳、上报）使用独立的探针key验证机制，排除JWT拦截</li>
     *   <li>监控指标查询接口（/api/metrics/probe/*、/api/statistics/**）当前设为公开访问</li>
     *   <li>Swagger和API文档相关路径始终排除</li>
     * </ul>
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // ============================================================
        // 第一层：速率限制拦截器
        // 仅对登录端点生效，防止恶意IP通过高频请求暴力破解密码
        // 配置：默认60秒内最多5次尝试，超过后封禁300秒
        // ============================================================
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/api/auth/login");  // 只对登录端点进行速率限制
        }

        // ============================================================
        // 第二层：JWT认证拦截器
        // 拦截所有 /api/** 路径的请求，验证JWT Token的有效性
        // 并检查方法上的@RequirePermission注解进行权限校验
        // ============================================================
        if (jwtInterceptor != null) {
            registry.addInterceptor(jwtInterceptor)
                    .addPathPatterns("/api/**")  // 拦截所有API请求
                    .excludePathPatterns(
                            // ------ 认证相关接口（无需登录即可访问）------
                            "/api/auth/login",                   // 登录接口：用户提交用户名密码获取Token
                            "/api/auth/register",                // 注册接口：新用户注册（如果有）
                            "/api/auth/refresh",                 // 刷新Token接口：使用refresh token换新access token
                            "/api/auth/generate-password",       // 临时密码生成接口：用于密码重置等场景

                            // ------ Agent探针通信接口（使用探针key验证，不走JWT）------
                            // 这些接口由数据探针Agent调用，Agent使用独立的UNIFIED_PROBE_KEY进行认证
                            "/api/agents/*/sync",                // Agent探针同步：Agent定期同步配置信息
                            "/api/agents/*/cdc-events",          // Agent CDC事件上报：变更数据捕获事件
                            "/api/agents/*/datasource-heartbeat", // Agent数据源心跳上报：监控数据源连接状态
                            "/api/agents/*/quality-rules",        // Agent拉取质量规则：Agent定期获取数据质量检查规则
                            "/api/agents/*/quality-bad-records",  // Agent上报坏记录：质量检查发现的问题数据

                            // ------ Agent插件接口（使用探针key验证）------
                            "/api/plugins/**",                   // Agent插件上报相关接口

                            // ------ 公开查询接口（当前无需认证）------
                            "/api/probes/online",                // 获取在线探针列表：用于监控大屏展示
                            "/api/metrics/probe/*",              // 查询探针指标数据：用于监控图表
                            "/api/metrics/probe/*/latest",       // 获取探针最新指标数据
                            "/api/metrics/probe/*/summary",      // 获取探针指标摘要

                            // ------ 临时公开接口（后续需根据安全需求收紧）------
                            "/api/statistics/**",                // 统计数据查询（公开访问）
                            "/api/audit-logs/**",                // 临时开放审计日志API用于测试
                            "/api/database-probes/**",           // 数据库探针API（临时开放）

                            // ------ 开发工具接口（生产环境应考虑关闭）------
                            "/swagger-ui/**",                    // Swagger UI：API文档可视化界面
                            "/v3/api-docs/**",                   // API文档：OpenAPI 3.0格式的接口文档
                            "/swagger-resources/**",             // Swagger资源
                            "/webjars/**",                       // WebJars：前端依赖库
                            "/error"                             // 错误页面：Spring默认错误处理
                    );
        }

        // ============================================================
        // 第三层：权限拦截器
        // 在JWT认证通过后，进一步检查用户是否拥有访问特定资源的权限
        // 基于@RequirePermission注解进行方法级权限控制
        // ============================================================
        if (permissionInterceptor != null) {
            registry.addInterceptor(permissionInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns(
                            // ------ 认证相关接口 ------
                            "/api/auth/**",                 // 所有认证接口（登录、注册、刷新、退出等）

                            // ------ Agent探针接口（使用探针key验证）------
                            "/api/probe/*/heartbeat",       // 探针心跳
                            "/api/metrics",                 // 探针上报数据（使用探针key验证）
                            "/api/metrics/*",               // 探针上报数据

                            // ------ 公开查询接口 ------
                            "/api/probes/online",           // 获取在线探针列表
                            "/api/metrics/probe/*",         // 查询探针指标数据
                            "/api/metrics/probe/*/latest",  // 获取探针最新指标数据
                            "/api/metrics/probe/*/summary", // 获取探针指标摘要

                            // ------ 临时公开接口 ------
                            "/api/statistics/**",           // 统计数据查询（公开访问）
                            "/api/audit-logs/**",           // 临时开放审计日志API用于测试
                            "/api/database-probes/**",      // 数据库探针API（临时开放）

                            // ------ 开发工具接口 ------
                            "/swagger-ui/**",               // Swagger UI
                            "/v3/api-docs/**",              // API文档
                            "/swagger-resources/**",        // Swagger资源
                            "/webjars/**",                  // WebJars
                            "/error"                        // 错误页面
                    );
        }
    }
}
