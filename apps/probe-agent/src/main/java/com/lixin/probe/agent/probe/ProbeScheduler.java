package com.lixin.probe.agent.probe;

import com.lixin.probe.agent.module.ModuleInstanceManager;
import com.lixin.probe.agent.module.ModuleManager;
import com.lixin.probe.agent.module.ModuleStatus;
import com.lixin.probe.agent.module.ProbeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 探针调度器
 * 负责管理探针的定时采集任务（支持多实例）
 *
 * @author Claude Code
 * @since 2.0
 * @version 2.0 (支持多实例模块)
 */
@Component
public class ProbeScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProbeScheduler.class);

    @Autowired
    private ModuleInstanceManager moduleInstanceManager;

    @Autowired
    private ModuleManager moduleManager;  // 保留用于兼容性

    @Autowired
    private ProbeStateManager stateManager;

    @Autowired(required = false)
    private TaskScheduler taskScheduler;

    // 探针定时任务存储
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 启动探针（多实例版本）
     *
     * @param probeKey 探针标识
     * @param probeType 探针类型
     * @return 是否启动成功
     */
    public boolean startProbe(String probeKey, ProbeType probeType) {
        if (stateManager.getState(probeKey) == ProbeState.RUNNING) {
            log.warn("探针已在运行中: probeKey={}", probeKey);
            return true;
        }

        try {
            log.info("启动探针（多实例）: probeKey={}, type={}", probeKey, probeType);

            // 更新状态为启动中
            stateManager.updateState(probeKey, ProbeState.STARTING, probeType.name());

            // 使用ModuleInstanceManager创建或获取模块实例
            if (moduleInstanceManager != null) {
                ModuleInstanceManager.ModuleInstance instance = moduleInstanceManager.getOrCreateModuleInstance(probeKey, probeType);

                if (instance == null) {
                    log.error("创建模块实例失败: probeKey={}, type={}", probeKey, probeType);
                    stateManager.updateState(probeKey, ProbeState.ERROR, probeType.name());
                    return false;
                }

                // 启动模块实例
                if (!instance.isRunning()) {
                    instance.getModule().start();
                    instance.setRunning(true);
                    log.info("探针模块实例启动成功: probeKey={}, type={}", probeKey, probeType);
                } else {
                    log.debug("探针模块实例已在运行: probeKey={}", probeKey);
                }
            }

            // 更新状态为运行中
            stateManager.updateState(probeKey, ProbeState.RUNNING, probeType.name());

            log.info("探针启动成功（多实例）: probeKey={}", probeKey);
            return true;

        } catch (Exception e) {
            log.error("启动探针失败（多实例）: probeKey={}", probeKey, e);
            stateManager.updateState(probeKey, ProbeState.ERROR, probeType.name());
            return false;
        }
    }

    /**
     * 停止探针（多实例版本）
     *
     * @param probeKey 探针标识
     * @param probeType 探针类型
     * @return 是否停止成功
     */
    public boolean stopProbe(String probeKey, ProbeType probeType) {
        ProbeState currentState = stateManager.getState(probeKey);
        if (currentState == ProbeState.STOPPED || currentState == ProbeState.UNKNOWN) {
            log.warn("探针已停止或不存在: probeKey={}, state={}", probeKey, currentState);
            return true;
        }

        try {
            log.info("停止探针（多实例）: probeKey={}, type={}", probeKey, probeType);

            // 更新状态为停止中
            stateManager.updateState(probeKey, ProbeState.STOPPING, probeType.name());

            // 取消所有定时任务
            ScheduledFuture<?> task = scheduledTasks.remove(probeKey);
            if (task != null) {
                task.cancel(false);
                log.debug("已取消探针定时任务: probeKey={}", probeKey);
            }

            // 使用ModuleInstanceManager停止模块实例
            if (moduleInstanceManager != null) {
                ModuleInstanceManager.ModuleInstance instance = moduleInstanceManager.getModuleInstance(probeKey);

                if (instance != null && instance.isRunning()) {
                    instance.getModule().stop();
                    instance.setRunning(false);
                    log.info("探针模块实例停止成功: probeKey={}, type={}", probeKey, probeType);
                } else {
                    log.debug("探针模块实例未运行: probeKey={}", probeKey);
                }
            }

            // 更新状态为已停止
            stateManager.updateState(probeKey, ProbeState.STOPPED, probeType.name());

            log.info("探针停止成功（多实例）: probeKey={}", probeKey);
            return true;

        } catch (Exception e) {
            log.error("停止探针失败（多实例）: probeKey={}", probeKey, e);
            stateManager.updateState(probeKey, ProbeState.ERROR, probeType.name());
            return false;
        }
    }

    /**
     * 重启探针
     *
     * @param probeKey 探针标识
     * @param probeType 探针类型
     * @return 是否重启成功
     */
    public boolean restartProbe(String probeKey, ProbeType probeType) {
        log.info("重启探针: probeKey={}, type={}", probeKey, probeType);

        // 先停止
        boolean stopped = stopProbe(probeKey, probeType);
        if (!stopped) {
            log.warn("停止探针失败，继续尝试重启: probeKey={}", probeKey);
        }

        // 等待一小段时间确保停止完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("重启等待被中断: probeKey={}", probeKey);
        }

        // 再启动
        boolean started = startProbe(probeKey, probeType);
        if (started) {
            log.info("探针重启成功: probeKey={}", probeKey);
        } else {
            log.error("探针重启失败: probeKey={}", probeKey);
            stateManager.updateState(probeKey, ProbeState.ERROR, probeType.name());
        }

        return started;
    }

    /**
     * 安排定时任务
     *
     * @param probeKey   探针标识
     * @param task       定时任务
     * @param delayMillis 延迟时间（毫秒）
     */
    public void scheduleTask(String probeKey, Runnable task, long delayMillis) {
        if (taskScheduler == null) {
            log.warn("TaskScheduler未配置，无法安排定时任务: probeKey={}", probeKey);
            return;
        }

        try {
            // 取消旧任务
            ScheduledFuture<?> oldTask = scheduledTasks.remove(probeKey);
            if (oldTask != null) {
                oldTask.cancel(false);
            }

            // 安排新任务
            ScheduledFuture<?> newTask = taskScheduler.scheduleAtFixedRate(
                task,
                Date.from(Instant.now().plusMillis(delayMillis)),
                delayMillis
            );
            scheduledTasks.put(probeKey, newTask);

            log.debug("已安排定时任务: probeKey={}, delay={}ms", probeKey, delayMillis);
        } catch (Exception e) {
            log.error("安排定时任务失败: probeKey={}", probeKey, e);
        }
    }

    /**
     * 取消探针的所有定时任务
     *
     * @param probeKey 探针标识
     */
    public void cancelTasks(String probeKey) {
        ScheduledFuture<?> task = scheduledTasks.remove(probeKey);
        if (task != null) {
            task.cancel(false);
            log.debug("已取消探针定时任务: probeKey={}", probeKey);
        }
    }

    /**
     * 获取探针数量
     *
     * @return 定时任务数量
     */
    public int getTaskCount() {
        return scheduledTasks.size();
    }

    /**
     * 清空所有定时任务
     */
    public void clear() {
        for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
            entry.getValue().cancel(false);
        }
        scheduledTasks.clear();
        log.info("已清空所有定时任务");
    }
}
