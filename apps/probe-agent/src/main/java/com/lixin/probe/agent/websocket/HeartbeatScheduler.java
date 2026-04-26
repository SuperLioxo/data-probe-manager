package com.lixin.probe.agent.websocket;

import com.lixin.probe.agent.config.DatabaseConfigManager;
import com.lixin.probe.agent.config.DatabaseConnectionConfig;
import com.lixin.probe.agent.probe.ProbeStateManager;
import com.lixin.probe.agent.probe.ProbeState;
import com.lixin.probe.agent.sync.ProbeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket心跳调度器
 *
 * <p>负责心跳消息的定时发送：
 * <ul>
 *   <li>Agent 级别心跳（保持连接）</li>
 *   <li>探针级别心跳（更新状态）</li>
 *   <li>启动心跳定时器</li>
 *   <li>停止心跳定时器</li>
 *   <li>可配置心跳间隔</li>
 * </ul></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 2.1 (携带探针状态信息)
 */
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);
    private final ScheduledExecutorService scheduler;
    private final MessageSender messageSender;
    private final ProbeSyncService probeSyncService;
    private final ProbeStateManager probeStateManager;
    private final DatabaseConfigManager databaseConfigManager;
    private final long intervalSeconds;
    private boolean isRunning = false;

    /**
     * 构造心跳调度器
     *
     * @param messageSender 消息发送器
     * @param probeSyncService 探针同步服务
     * @param probeStateManager 探针状态管理器
     * @param databaseConfigManager 数据库配置管理器
     * @param intervalSeconds 心跳间隔（秒）
     */
    public HeartbeatScheduler(MessageSender messageSender, ProbeSyncService probeSyncService,
                             ProbeStateManager probeStateManager, DatabaseConfigManager databaseConfigManager,
                             long intervalSeconds) {
        this.messageSender = messageSender;
        this.probeSyncService = probeSyncService;
        this.probeStateManager = probeStateManager;
        this.databaseConfigManager = databaseConfigManager;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "heartbeat-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 构造心跳调度器（默认30秒间隔）
     *
     * @param messageSender 消息发送器
     * @param probeSyncService 探针同步服务
     * @param probeStateManager 探针状态管理器
     * @param databaseConfigManager 数据库配置管理器
     */
    public HeartbeatScheduler(MessageSender messageSender, ProbeSyncService probeSyncService,
                             ProbeStateManager probeStateManager, DatabaseConfigManager databaseConfigManager) {
        this(messageSender, probeSyncService, probeStateManager, databaseConfigManager, 30);
    }

    /**
     * 启动心跳定时器
     */
    public void start() {
        if (isRunning) {
            log.warn("心跳调度器已经在运行");
            return;
        }

        // 先立即发送第一次心跳（避免启动后显示离线）
        try {
            log.info("[心跳调度器] 启动后立即发送第一次心跳...");
            Map<String, String> probeStates = collectProbeStates();
            messageSender.sendHeartbeat(probeStates);
            log.info("[心跳调度器] 第一次心跳发送成功");
        } catch (Exception e) {
            log.error("[心跳调度器] 第一次心跳发送失败", e);
        }

        // 然后启动定时心跳任务
        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.debug("[心跳调度器] 开始发送心跳...");

                // 1. 收集所有探针状态
                Map<String, String> probeStates = collectProbeStates();

                // 2. 记录心跳内容（调试）
                if (log.isDebugEnabled()) {
                    log.debug("[心跳调度器] 准备发送心跳，探针状态: {}", probeStates);
                }

                // 3. 发送 Agent 级别心跳（携带探针状态）
                messageSender.sendHeartbeat(probeStates);

            } catch (Exception e) {
                log.error("心跳发送失败", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        isRunning = true;
        log.info("心跳定时器已启动，间隔: {} 秒（已立即发送第一次心跳）", intervalSeconds);
    }

    /**
     * 收集所有探针状态
     *
     * <p>统一收集所有探针（SYSTEM、DATABASE、FILE）的状态用于心跳上报：
     * <ul>
     *   <li>优先从ProbeSyncService获取所有已同步的探针（SYSTEM、FILE探针）</li>
     *   <li>再从DatabaseConfigManager获取DATABASE探针状态</li>
     *   <li>最后从ProbeStateManager获取实际运行中的探针状态</li>
     *   <li>合并三者，确保所有探针都上报心跳</li>
     * </ul></p>
     *
     * @return 探针状态映射（probeKey -> stateCode）
     */
    private Map<String, String> collectProbeStates() {
        Map<String, String> states = new HashMap<>();

        try {
            // 1. 从ProbeSyncService获取所有已同步的探针（SYSTEM、FILE探针）
            if (probeSyncService != null) {
                var probes = probeSyncService.getAllProbes();
                if (probes != null && !probes.isEmpty()) {
                    log.info("[心跳调度器] 从ProbeSyncService收集到 {} 个探针，详细信息:", probes.size());
                    for (var probe : probes) {
                        log.info("  - probeKey={}, type={}, status={}, host={}:{}",
                            probe.getProbeKey(), probe.getType(), probe.getStatus(),
                            probe.getHostIp(), probe.getPort());
                        // 默认状态为RUNNING（探针已同步说明配置存在）
                        states.put(probe.getProbeKey(), "running");
                    }
                    log.info("[心跳调度器] 从ProbeSyncService收集到 {} 个探针", probes.size());
                } else {
                    log.warn("[心跳调度器] ProbeSyncService返回空探针列表");
                }
            } else {
                log.warn("[心跳调度器] ProbeSyncService为null");
            }

            // 2. 从DatabaseConfigManager获取DATABASE探针状态（如果有）
            if (databaseConfigManager != null) {
                try {
                    var dbConfigs = databaseConfigManager.getAllDatabaseConfigs();
                    if (dbConfigs != null && !dbConfigs.isEmpty()) {
                        for (var dbConfig : dbConfigs) {
                            if (dbConfig.getProbeKey() != null && !dbConfig.getProbeKey().isEmpty()) {
                                // DATABASE探针状态由DatabaseModule的running状态决定
                                // 这里默认设置为running，具体状态由DatabaseModule管理
                                states.put(dbConfig.getProbeKey(), "running");
                            }
                        }
                        log.info("[心跳调度器] 从DatabaseConfigManager收集到 {} 个DATABASE探针", dbConfigs.size());
                    }
                } catch (Exception e) {
                    log.debug("[心跳调度器] 收集DATABASE探针状态失败（可能未启用）: {}", e.getMessage());
                }
            }

            // 3. 从ProbeStateManager获取实际运行中的探针状态，覆盖默认状态
            if (probeStateManager != null) {
                Map<String, ProbeState> allStates = probeStateManager.getAllStates();
                for (Map.Entry<String, ProbeState> entry : allStates.entrySet()) {
                    // 使用实际运行状态覆盖默认的RUNNING状态
                    states.put(entry.getKey(), entry.getValue().getCode());
                }
                log.debug("[心跳调度器] 从ProbeStateManager更新 {} 个运行中探针状态", allStates.size());
            }

            // 4. 强制设置FILE探针状态为running（FILE探针是被动触发，不在ProbeStateManager中）
            if (probeSyncService != null) {
                var probes = probeSyncService.getAllProbes();
                if (probes != null && !probes.isEmpty()) {
                    for (var probe : probes) {
                        if ("FILE".equals(probe.getType())) {
                            // FILE探针只要已同步，就默认为running状态
                            states.put(probe.getProbeKey(), "running");
                            log.info("[心跳调度器] FILE探针强制设置为running: {}", probe.getProbeKey());
                        }
                    }
                }
            }

            log.info("[心跳调度器] 总共收集 {} 个探针状态用于心跳上报", states.size());

        } catch (Exception e) {
            log.error("收集探针状态失败", e);
        }

        return states;
    }

    /**
     * 停止心跳定时器
     */
    public void stop() {
        if (!isRunning) {
            return;
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        isRunning = false;
        log.info("心跳定时器已停止");
    }

    /**
     * 检查是否正在运行
     *
     * @return 如果正在运行返回true
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 获取心跳间隔
     *
     * @return 心跳间隔（秒）
     */
    public long getIntervalSeconds() {
        return intervalSeconds;
    }

    /**
     * 销毁调度器
     */
    public void destroy() {
        stop();
    }
}
