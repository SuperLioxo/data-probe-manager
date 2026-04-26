package com.lixin.probe.agent.aspect;

import com.lixin.probe.agent.annotation.RateLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 请求限流切面
 * 基于令牌桶算法实现API限流
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);
    /**
     * 请求计数器
     * key: 限流key, value: {count, expireTime}
     */
    private static final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 获取请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();

        // 生成限流key
        String key = generateKey(request, rateLimit);

        // 检查是否超过限流
        RateLimiter limiter = limiters.computeIfAbsent(key, k -> new RateLimiter());

        if (!limiter.tryAcquire(rateLimit.time(), rateLimit.count())) {
            log.warn("请求限流触发: key={}, uri={}, ip={}",
                    key, request.getRequestURI(), getRemoteIp(request));

            // 返回限流错误
            return buildRateLimitResponse(rateLimit.message(), rateLimit.time(), rateLimit.count());
        }

        return joinPoint.proceed();
    }

    /**
     * 生成限流key
     */
    private String generateKey(HttpServletRequest request, RateLimit rateLimit) {
        String remoteIp = getRemoteIp(request);
        String uri = request.getRequestURI();
        return "rate_limit:" + remoteIp + ":" + uri;
    }

    /**
     * 获取客户端IP地址
     */
    private String getRemoteIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个IP的情况（取第一个）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 构建限流响应
     */
    private Object buildRateLimitResponse(String message, int time, int count) {
        return new RateLimitResponse(429, "Too Many Requests", message, time, count);
    }

    /**
     * 限流器
     */
    private static class RateLimiter {
        private final AtomicInteger counter = new AtomicInteger(0);
        private volatile long expireTime = 0;

        /**
         * 尝试获取令牌
         *
         * @param time  时间窗口（秒）
         * @param count 最大请求次数
         * @return true=获取成功, false=超过限流
         */
        public synchronized boolean tryAcquire(int time, int count) {
            long now = System.currentTimeMillis();

            // 检查是否已过期
            if (now >= expireTime) {
                // 重置计数器
                counter.set(0);
                expireTime = now + time * 1000L;
            }

            // 检查是否超过限流
            if (counter.get() >= count) {
                return false;
            }

            // 增加计数
            counter.incrementAndGet();
            return true;
        }
    }

    /**
     * 限流响应
     */
    public static class RateLimitResponse {
        private final int code;
        private final String error;
        private final String message;
        private final int timeWindow;
        private final int maxRequests;

        public RateLimitResponse(int code, String error, String message, int timeWindow, int maxRequests) {
            this.code = code;
            this.error = error;
            this.message = message;
            this.timeWindow = timeWindow;
            this.maxRequests = maxRequests;
        }

        public int getCode() {
            return code;
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }

        public int getTimeWindow() {
            return timeWindow;
        }

        public int getMaxRequests() {
            return maxRequests;
        }
    }
}
