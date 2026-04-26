package com.lixin.probe.agent.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Component
public class TaskExecutorManager {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutorManager.class);

    private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> timeouts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "task-timeout");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ExecutorStats> stats = new ConcurrentHashMap<>();

    private int defaultMaxThreads = 4;
    private int defaultQueueCapacity = 100;
    private int defaultTimeoutSeconds = 300;

    public <T> Future<T> submit(String taskKey, Callable<T> task) {
        return submit(taskKey, task, defaultTimeoutSeconds);
    }

    public <T> Future<T> submit(String taskKey, Callable<T> task, int timeoutSeconds) {
        ExecutorService executor = getOrCreate(taskKey);

        Future<T> future = executor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                T result = task.call();
                updateStats(taskKey, true, System.currentTimeMillis() - start);
                return result;
            } catch (Exception e) {
                updateStats(taskKey, false, System.currentTimeMillis() - start);
                throw e;
            }
        });

        ScheduledFuture<?> timeout = timeoutScheduler.schedule(() -> {
            if (!future.isDone()) {
                future.cancel(true);
                log.warn("Task timed out: key={}, timeout={}s", taskKey, timeoutSeconds);
            }
        }, timeoutSeconds, TimeUnit.SECONDS);

        timeouts.put(taskKey, timeout);
        return future;
    }

    public Future<?> submit(String taskKey, Runnable task) {
        return submit(taskKey, Executors.callable(task, null));
    }

    public Future<?> submit(String taskKey, Runnable task, int timeoutSeconds) {
        return submit(taskKey, Executors.callable(task, null), timeoutSeconds);
    }

    public void shutdown(String taskKey) {
        ExecutorService executor = executors.remove(taskKey);
        ScheduledFuture<?> timeout = timeouts.remove(taskKey);
        stats.remove(taskKey);

        if (timeout != null) timeout.cancel(false);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void shutdownAll() {
        executors.keySet().forEach(this::shutdown);
        timeoutScheduler.shutdown();
        log.info("All task executors shut down");
    }

    public Map<String, ExecutorStats> getStats() {
        return Map.copyOf(stats);
    }

    public void setDefaults(int maxThreads, int queueCapacity, int timeoutSeconds) {
        this.defaultMaxThreads = maxThreads;
        this.defaultQueueCapacity = queueCapacity;
        this.defaultTimeoutSeconds = timeoutSeconds;
    }

    private ExecutorService getOrCreate(String taskKey) {
        return executors.computeIfAbsent(taskKey, k ->
                new ThreadPoolExecutor(
                        1, defaultMaxThreads,
                        60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(defaultQueueCapacity),
                        r -> new Thread(r, "task-" + taskKey),
                        new ThreadPoolExecutor.CallerRunsPolicy()
                )
        );
    }

    private void updateStats(String taskKey, boolean success, long durationMs) {
        stats.compute(taskKey, (k, existing) -> {
            if (existing == null) {
                return new ExecutorStats(success ? 1 : 0, success ? 0 : 1, durationMs);
            }
            return new ExecutorStats(
                    existing.completedTasks() + (success ? 1 : 0),
                    existing.failedTasks() + (success ? 0 : 1),
                    (existing.totalDurationMs() + durationMs) / (existing.completedTasks() + existing.failedTasks() + 1)
            );
        });
    }
}
