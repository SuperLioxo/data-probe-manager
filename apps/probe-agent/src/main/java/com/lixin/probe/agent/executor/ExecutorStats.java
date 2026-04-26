package com.lixin.probe.agent.executor;

public record ExecutorStats(long completedTasks, long failedTasks, long avgDurationMs) {
    public long totalDurationMs() {
        return avgDurationMs * (completedTasks + failedTasks);
    }
}
