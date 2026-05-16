package com.lixin.probe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 登录速率限制拦截器 —— 防暴力破解攻击的第一道防线
 *
 * <p>本拦截器通过Redis实现基于IP地址的滑动窗口限流，专门用于保护登录接口。
 * 当某个IP在规定时间窗口内的登录尝试次数超过阈值时，该IP将被临时封禁，
 * 在封禁期间的所有登录请求将被直接拒绝。</p>
 *
 * <h3>限流算法（基于Redis的计数器限流）：</h3>
 * <pre>
 *   请求到达
 *     │
 *     ▼
 *   是否为登录请求？ ──否──→ 直接放行
 *     │ 是
 *     ▼
 *   Redis可用？ ──否──→ 降级放行（不阻塞正常业务）
 *     │ 是
 *     ▼
 *   IP已被封禁？ ──是──→ 返回429 Too Many Requests
 *     │ 否
 *     ▼
 *   增加尝试计数
 *     │
 *     ▼
 *   超过最大次数？ ──是──→ 封禁IP，返回429
 *     │ 否
 *     ▼
 *   放行，继续登录流程
 * </pre>
 *
 * <h3>可配置参数：</h3>
 * <ul>
 *   <li><b>rate.limit.login.maxAttempts</b> —— 时间窗口内最大尝试次数，默认5次</li>
 *   <li><b>rate.limit.login.window</b> —— 计数时间窗口（秒），默认60秒</li>
 *   <li><b>rate.limit.login.blockDuration</b> —— 超限后封禁时长（秒），默认300秒（5分钟）</li>
 * </ul>
 *
 * <h3>Redis键设计：</h3>
 * <ul>
 *   <li>计数键：<code>rate_limit:login:{clientIp}</code> —— 记录尝试次数，带TTL自动过期</li>
 *   <li>封禁键：<code>rate_limit:login:{clientIp}:blocked</code> —— 标识IP被封禁，TTL为封禁时长</li>
 * </ul>
 *
 * <h3>降级策略：</h3>
 * <p>当Redis不可用时（如Redis连接故障），拦截器会跳过限流检查并放行请求，
 * 避免因Redis故障导致所有用户无法登录。</p>
 *
 * @see WebConfig WebConfig中注册本拦截器，仅对/api/auth/login路径生效
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    /** Redis键前缀，用于区分不同类型的限流（目前只有登录限流） */
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    /**
     * Redis模板（可选注入）
     * 使用Redis存储请求计数和封禁状态，实现分布式的速率限制。
     * 如果Redis不可用，限流功能将自动降级为放行模式。
     */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 最大尝试次数
     * 在一个时间窗口内允许的最大登录尝试次数，默认5次。
     * 超过此次数后IP将被封禁。
     */
    private int maxAttempts = 5;

    /**
     * 时间窗口（秒）
     * 计数器的时间范围，默认60秒。
     * 在此时间范围内累计登录尝试次数。
     */
    private int windowInSeconds = 60;

    /**
     * 封禁时长（秒）
     * IP被封禁后的冷却时间，默认300秒（5分钟）。
     * 封禁期间该IP的所有登录请求将被拒绝。
     */
    private int blockDurationInSeconds = 300;

    /**
     * 设置最大尝试次数
     *
     * @param maxAttempts 最大尝试次数
     */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * 设置时间窗口
     *
     * @param windowInSeconds 时间窗口（秒）
     */
    public void setWindowInSeconds(int windowInSeconds) {
        this.windowInSeconds = windowInSeconds;
    }

    /**
     * 设置封禁时长
     *
     * @param blockDurationInSeconds 封禁时长（秒）
     */
    public void setBlockDurationInSeconds(int blockDurationInSeconds) {
        this.blockDurationInSeconds = blockDurationInSeconds;
    }

    /**
     * 请求预处理 —— 执行速率限制检查
     *
     * <p>拦截器核心逻辑，在每个请求到达Controller之前执行。
     * 仅对POST /api/auth/login请求生效，其他请求直接放行。</p>
     *
     * <p>处理步骤：</p>
     * <ol>
     *   <li>判断是否为登录请求，非登录请求直接放行</li>
     *   <li>检查Redis可用性，不可用时降级放行</li>
     *   <li>检查IP是否处于封禁状态</li>
     *   <li>递增尝试计数并设置过期时间</li>
     *   <li>判断是否超限，超限则封禁IP</li>
     * </ol>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  目标处理器
     * @return true表示放行，false表示拒绝请求
     * @throws Exception 处理异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) throws Exception {

        // 只对登录端点进行速率限制，其他请求不受影响
        if (!isLoginRequest(request)) {
            return true;
        }

        // Redis降级策略：如果Redis不可用，跳过限流检查
        // 这保证了即使Redis宕机，用户仍然可以正常登录（牺牲安全性换取可用性）
        if (redisTemplate == null) {
            log.warn("Redis 不可用，跳过速率限制");
            return true;
        }

        // 获取客户端真实IP地址（支持代理环境）
        String clientIp = getClientIp(request);
        // 构建Redis计数键，格式：rate_limit:login:{clientIp}
        String key = RATE_LIMIT_PREFIX + "login:" + clientIp;

        // ====== 检查IP是否已被封禁 ======
        // 封禁键格式：rate_limit:login:{clientIp}:blocked
        String blockedKey = key + ":blocked";
        Boolean isBlocked = redisTemplate.hasKey(blockedKey);

        if (Boolean.TRUE.equals(isBlocked)) {
            // IP已被封禁，获取剩余封禁时间用于提示用户
            Long ttl = redisTemplate.getExpire(blockedKey, TimeUnit.SECONDS);
            log.warn("登录请求被拒绝：IP {} 被封禁，剩余时间：{} 秒", clientIp, ttl);

            // 返回429 Too Many Requests状态码
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format(
                    "{\"code\":429,\"message\":\"登录尝试过多，请 %d 秒后再试\"}",
                    ttl != null ? ttl : blockDurationInSeconds
            ));
            return false;
        }

        // ====== 递增尝试计数 ======
        // 使用Redis的INCR命令原子性地增加计数
        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts == null) {
            attempts = 1L;
        }

        // 第一次尝试时设置过期时间（后续请求不会重置TTL）
        // 这实现了固定窗口的限流算法
        if (attempts == 1) {
            redisTemplate.expire(key, windowInSeconds, TimeUnit.SECONDS);
        }

        // ====== 检查是否超过最大尝试次数 ======
        if (attempts > maxAttempts) {
            log.warn("登录尝试次数过多：IP {} 在 {} 秒内尝试了 {} 次",
                    clientIp, windowInSeconds, attempts);

            // 将该IP加入封禁列表，封禁时长为blockDurationInSeconds
            redisTemplate.opsForValue().set(blockedKey, "1", blockDurationInSeconds, TimeUnit.SECONDS);

            // 删除计数器（封禁解除后会重新计数）
            redisTemplate.delete(key);

            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format(
                    "{\"code\":429,\"message\":\"登录尝试过多，请 %d 秒后再试\"}",
                    blockDurationInSeconds
            ));
            return false;
        }

        log.info("登录尝试：IP {}，第 {} 次", clientIp, attempts);
        return true;
    }

    /**
     * 判断是否为登录请求
     *
     * <p>仅对 POST /api/auth/login 请求进行限流，确保其他API接口不受影响。</p>
     *
     * @param request HTTP请求对象
     * @return true表示是登录请求
     */
    private boolean isLoginRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        return "/api/auth/login".equals(uri) && "POST".equalsIgnoreCase(method);
    }

    /**
     * 获取客户端真实IP地址
     *
     * <p>在反向代理（如Nginx）环境下，request.getRemoteAddr()获取的是代理服务器的IP，
     * 而非客户端真实IP。因此需要从代理转发的请求头中提取真实IP。</p>
     *
     * <p>优先级：</p>
     * <ol>
     *   <li>X-Forwarded-For —— 标准代理头，可能包含多个IP（取第一个）</li>
     *   <li>X-Real-IP —— Nginx等代理常用的自定义头</li>
     *   <li>request.getRemoteAddr() —— 直连时的客户端IP</li>
     * </ol>
     *
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For可能包含多个IP（经过多层代理时），取第一个为客户端真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 清除指定IP的速率限制计数器
     *
     * <p>当用户成功登录后调用此方法重置该IP的尝试计数，避免用户因之前输入错误密码
     * 导致的计数残留影响后续登录。</p>
     *
     * @param clientIp 客户端IP地址
     */
    public void resetRateLimit(String clientIp) {
        if (redisTemplate != null) {
            String key = RATE_LIMIT_PREFIX + "login:" + clientIp;
            redisTemplate.delete(key);
            log.info("清除速率限制：IP {}", clientIp);
        }
    }
}
