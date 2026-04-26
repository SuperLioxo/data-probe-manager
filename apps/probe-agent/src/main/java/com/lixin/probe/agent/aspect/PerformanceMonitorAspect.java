package com.lixin.probe.agent.aspect;

import com.lixin.probe.agent.util.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控切面
 * 自动记录方法执行时间，统计调用次数和平均耗时
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Aspect
@Component
public class PerformanceMonitorAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitorAspect.class);
    /**
     * 方法调用统计
     */
    private static final ConcurrentHashMap<String, MethodStats> methodStatsMap = new ConcurrentHashMap<>();

    /**
     * 性能阈值（毫秒）
     * 超过此阈值的方法执行会记录警告日志
     */
    private static final long SLOW_METHOD_THRESHOLD = 1000;

    /**
     * 定义切点：所有Service层的方法
     */
    @Pointcut("execution(* com.lixin.probe.agent.service..*.*(..))")
    public void serviceLayer() {
    }

    /**
     * 定义切点：所有Controller层的公共方法
     */
    @Pointcut("execution(* com.lixin.probe.agent.controller..*.*(..))")
    public void controllerLayer() {
    }

    /**
     * 定义切点：所有Module层的方法
     */
    @Pointcut("execution(* com.lixin.probe.agent.module..*.*(..))")
    public void moduleLayer() {
    }

    /**
     * 环绕通知：监控方法执行性能
     */
    @Around("serviceLayer() || controllerLayer() || moduleLayer()")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();

        try {
            // 执行目标方法
            Object result = joinPoint.proceed();

            // 记录成功执行
            long duration = System.currentTimeMillis() - startTime;
            recordExecution(methodName, duration, true);

            // 慢方法警告
            if (duration > SLOW_METHOD_THRESHOLD) {
                log.warn("检测到慢方法: {} | 耗时: {} ms", methodName, duration);
            }

            return result;

        } catch (Exception e) {
            // 记录失败执行
            long duration = System.currentTimeMillis() - startTime;
            recordExecution(methodName, duration, false);

            log.error("方法执行异常: {} | 耗时: {} ms | 错误: {}",
                    methodName, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * 记录方法执行统计
     */
    private void recordExecution(String methodName, long duration, boolean success) {
        MethodStats stats = methodStatsMap.computeIfAbsent(methodName, k -> new MethodStats());

        stats.totalCount.incrementAndGet();
        stats.totalTime.addAndGet(duration);

        if (success) {
            stats.successCount.incrementAndGet();
        } else {
            stats.failureCount.incrementAndGet();
        }

        // 更新最小/最大耗时
        stats.minDuration.updateAndGet(current -> Math.min(current, duration));
        stats.maxDuration.updateAndGet(current -> Math.max(current, duration));
    }

    /**
     * 获取所有方法统计信息
     */
    public static java.util.Map<String, Object> getStatistics() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();

        methodStatsMap.forEach((method, stats) -> {
            java.util.Map<String, Object> methodData = new java.util.LinkedHashMap<>();
            long totalCount = stats.totalCount.get();
            long totalTime = stats.totalTime.get();

            methodData.put("totalCount", totalCount);
            methodData.put("successCount", stats.successCount.get());
            methodData.put("failureCount", stats.failureCount.get());
            methodData.put("avgDuration", totalCount > 0 ? totalTime / totalCount : 0);
            methodData.put("minDuration", stats.minDuration.get());
            methodData.put("maxDuration", stats.maxDuration.get());
            methodData.put("totalTime", totalTime);

            result.put(method, methodData);
        });

        return result;
    }

    /**
     * 重置统计信息
     */
    public static void resetStatistics() {
        methodStatsMap.clear();
        log.info("性能监控统计已重置");
    }

    /**
     * 方法统计信息
     */
    private static class MethodStats {
        final AtomicLong totalCount = new AtomicLong(0);
        final AtomicLong successCount = new AtomicLong(0);
        final AtomicLong failureCount = new AtomicLong(0);
        final AtomicLong totalTime = new AtomicLong(0);
        final AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxDuration = new AtomicLong(0);
    }
}
