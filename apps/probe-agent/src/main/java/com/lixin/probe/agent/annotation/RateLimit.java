package com.lixin.probe.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求限流注解
 * 用于限制API请求频率
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流key（支持SpEL表达式）
     * 默认使用当前请求的IP地址
     */
    String key() default "'rate_limit:' + #request.remoteAddr";

    /**
     * 时间窗口（秒）
     */
    int time() default 60;

    /**
     * 时间窗口内最大请求次数
     */
    int count() default 100;

    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试 / Too many requests, please try again later";
}
