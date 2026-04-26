package com.lixin.probe.aspect;

import com.lixin.probe.annotation.RateLimit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 速率限制切面
 * 使用令牌桶算法实现接口限流
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitAspect.class);

    /**
     * 本地缓存，存储每个限制键对应的令牌桶
     * 注意：在分布式环境中应该使用Redis或其他分布式缓存
     */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * 环绕通知，拦截带有@RateLimit注解的方法
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = getRequest();
        if (request == null) {
            // 如果没有请求上下文，直接放行
            return joinPoint.proceed();
        }

        // 获取限制键
        String limitKey = rateLimit.key().isEmpty() ?
                getDefaultKey(joinPoint) : rateLimit.key();

        // 获取客户端标识（IP或探针Key）
        String clientId = getClientIdentifier(request);

        // 组合最终的键：限制键 + 客户端标识
        String finalKey = limitKey + ":" + clientId;

        // 获取或创建令牌桶
        Bucket bucket = buckets.computeIfAbsent(finalKey, k -> createBucket(rateLimit));

        // 尝试消费一个令牌
        if (bucket.tryConsume(1)) {
            // 有令牌可用，放行请求
            return joinPoint.proceed();
        } else {
            // 没有令牌可用，限流
            log.warn("速率限制触发: key={}, ip={}, uri={}",
                    finalKey, request.getRemoteAddr(), request.getRequestURI());

            // 抛出异常或返回错误响应
            throw new RuntimeException("请求过于频繁，请稍后再试");
        }
    }

    /**
     * 创建令牌桶
     *
     * @param rateLimit 速率限制注解
     * @return 令牌桶对象
     */
    private Bucket createBucket(RateLimit rateLimit) {
        // 计算令牌补充的时间间隔
        long refillPeriodNanos = rateLimit.timeUnit().toNanos(1) / rateLimit.refillRate();

        // 创建带宽限制
        Bandwidth limit = Bandwidth.classic(rateLimit.capacity(),
                Refill.greedy(rateLimit.refillRate(),
                        Duration.ofNanos(refillPeriodNanos)));

        // 创建并返回令牌桶
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * 获取当前HTTP请求
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取客户端标识符
     * 优先使用探针Key，其次使用IP地址
     */
    private String getClientIdentifier(HttpServletRequest request) {
        // 尝试从请求参数获取探针Key
        String probeKey = request.getParameter("probeKey");
        if (probeKey != null && !probeKey.isEmpty()) {
            return "probe:" + probeKey;
        }

        // 尝试从路径变量获取探针Key
        String uri = request.getRequestURI();
        if (uri.contains("/probe/")) {
            String[] parts = uri.split("/");
            for (int i = 0; i < parts.length; i++) {
                if ("probe".equals(parts[i]) && i + 1 < parts.length) {
                    return "probe:" + parts[i + 1];
                }
            }
        }

        // 使用IP地址作为默认标识
        return "ip:" + request.getRemoteAddr();
    }

    /**
     * 获取默认的限制键
     * 使用类名+方法名作为默认键
     */
    private String getDefaultKey(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        return className + ":" + methodName;
    }

    /**
     * 清理过期的令牌桶（可以由定时任务调用）
     * 在长时间运行的应用中，防止内存泄漏
     */
    public void cleanupExpiredBuckets() {
        // 简单的清理策略：如果桶数量超过阈值，清空所有桶
        if (buckets.size() > 10000) {
            log.info("令牌桶数量超过阈值，执行清理: currentSize={}", buckets.size());
            buckets.clear();
        }
    }

    /**
     * 获取当前令牌桶数量（用于监控）
     */
    public int getBucketCount() {
        return buckets.size();
    }
}
