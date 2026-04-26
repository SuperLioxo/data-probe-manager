package com.lixin.probe.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 速率限制注解
 * 用于限制接口的调用频率，防止DDoS攻击
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /**
     * 令牌桶容量
     * 表示在时间窗口内最多允许的请求数
     */
    long capacity() default 100;

    /**
     * 令牌补充速率（每秒补充的令牌数）
     * 表示系统每秒能够处理的请求数
     */
    long refillRate() default 10;

    /**
     * 时间单位
     * 用于计算令牌补充速率的时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 限制键
     * 用于区分不同的限制策略
     * 默认为空字符串，表示使用方法名作为键
     * 可以自定义，例如 "metric-report" 或 "login"
     */
    String key() default "";
}
