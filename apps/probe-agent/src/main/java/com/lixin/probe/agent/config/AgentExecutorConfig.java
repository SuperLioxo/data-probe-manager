package com.lixin.probe.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent线程池配置
 * 统一管理所有异步任务的线程池
 *
 * @author Claude Code
 * @date 2026-03-22
 */
@Configuration
public class AgentExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutorConfig.class);

    /**
     * Agent任务执行器
     * 用于处理异步任务，避免手动创建Thread
     */
    @Bean(name = "agentTaskExecutor")
    public ThreadPoolTaskExecutor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数
        executor.setCorePoolSize(4);

        // 最大线程数
        executor.setMaxPoolSize(10);

        // 队列容量
        executor.setQueueCapacity(100);

        // 线程名前缀
        executor.setThreadNamePrefix("agent-task-");

        // 空闲线程存活时间
        executor.setKeepAliveSeconds(60);

        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待任务完成后再关闭（优雅关闭）
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("Agent线程池初始化完成: coreSize={}, maxSize={}, queueCapacity={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }

    /**
     * 用于@Async注解的默认执行器
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        return agentTaskExecutor();
    }
}
