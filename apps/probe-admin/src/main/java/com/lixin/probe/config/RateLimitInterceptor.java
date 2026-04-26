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
 * 登录速率限制拦截器
 *
 * <p>防止暴力破解攻击，限制每个 IP 地址的登录尝试次数。</p>
 *
 * <p>配置参数：</p>
 * <ul>
 *   <li>rate.limit.login.maxAttempts: 最大尝试次数（默认 5 次）</li>
 *   <li>rate.limit.login.window: 时间窗口（秒，默认 60 秒）</li>
 *   <li>rate.limit.login.blockDuration: 封禁时长（秒，默认 300 秒）</li>
 * </ul>
 *
 * @author Claude Code
 * @since 1.0.0
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 最大尝试次数
     */
    private int maxAttempts = 5;

    /**
     * 时间窗口（秒）
     */
    private int windowInSeconds = 60;

    /**
     * 封禁时长（秒）
     */
    private int blockDurationInSeconds = 300;

    /**
     * 设置最大尝试次数
     */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * 设置时间窗口
     */
    public void setWindowInSeconds(int windowInSeconds) {
        this.windowInSeconds = windowInSeconds;
    }

    /**
     * 设置封禁时长
     */
    public void setBlockDurationInSeconds(int blockDurationInSeconds) {
        this.blockDurationInSeconds = blockDurationInSeconds;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) throws Exception {

        // 只对登录端点进行速率限制
        if (!isLoginRequest(request)) {
            return true;
        }

        // 如果 Redis 不可用，跳过速率限制
        if (redisTemplate == null) {
            log.warn("Redis 不可用，跳过速率限制");
            return true;
        }

        String clientIp = getClientIp(request);
        String key = RATE_LIMIT_PREFIX + "login:" + clientIp;

        // 检查是否被封禁
        String blockedKey = key + ":blocked";
        Boolean isBlocked = redisTemplate.hasKey(blockedKey);

        if (Boolean.TRUE.equals(isBlocked)) {
            Long ttl = redisTemplate.getExpire(blockedKey, TimeUnit.SECONDS);
            log.warn("登录请求被拒绝：IP {} 被封禁，剩余时间：{} 秒", clientIp, ttl);

            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format(
                    "{\"code\":429,\"message\":\"登录尝试过多，请 %d 秒后再试\"}",
                    ttl != null ? ttl : blockDurationInSeconds
            ));
            return false;
        }

        // 增加尝试次数
        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts == null) {
            attempts = 1L;
        }

        // 设置过期时间（第一次尝试时）
        if (attempts == 1) {
            redisTemplate.expire(key, windowInSeconds, TimeUnit.SECONDS);
        }

        // 检查是否超过最大尝试次数
        if (attempts > maxAttempts) {
            log.warn("登录尝试次数过多：IP {} 在 {} 秒内尝试了 {} 次",
                    clientIp, windowInSeconds, attempts);

            // 封禁该 IP
            redisTemplate.opsForValue().set(blockedKey, "1", blockDurationInSeconds, TimeUnit.SECONDS);

            // 删除计数器
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
     */
    private boolean isLoginRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        return "/api/auth/login".equals(uri) && "POST".equalsIgnoreCase(method);
    }

    /**
     * 获取客户端真实 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果有多个代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 清除指定 IP 的速率限制
     * 用于用户成功登录后重置计数
     */
    public void resetRateLimit(String clientIp) {
        if (redisTemplate != null) {
            String key = RATE_LIMIT_PREFIX + "login:" + clientIp;
            redisTemplate.delete(key);
            log.info("清除速率限制：IP {}", clientIp);
        }
    }
}
