package com.lixin.probe.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * Lock key. Supports SpEL expressions: #task.id, #probeKey, etc.
     */
    String key();

    /**
     * Maximum time to wait for lock acquisition.
     */
    long waitTime() default 5;

    /**
     * Maximum time to hold the lock. Must be longer than method execution time.
     */
    long leaseTime() default 30;

    /**
     * Time unit for waitTime and leaseTime.
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
