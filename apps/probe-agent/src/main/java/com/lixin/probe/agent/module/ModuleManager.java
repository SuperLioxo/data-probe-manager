package com.lixin.probe.agent.module;

import com.lixin.probe.agent.constant.Command;
import com.lixin.probe.agent.sync.ProbeSyncService;
import com.lixin.probe.agent.websocket.WebSocketClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 模块管理器
 * 负责管理所有探针模块的生命周期
 * 根据ProbeSyncService同步的探针配置动态启动/停止模块
 */
@Component
public class ModuleManager {

    private static final Logger log = LoggerFactory.getLogger(ModuleManager.class);

    @Autowired(required = false)
    private List<ProbeModule> modules;

    @Autowired
    private WebSocketClientHandler wsClient;

    @Autowired(required = false)
    private ProbeSyncService probeSyncService;

    private final Map<ProbeType, ProbeModule> moduleMap = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    // 记录上次同步的探针类型集合（线程安全）
    private final Set<ProbeType> lastSyncedTypes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Autowired(required = false)
    private ThreadPoolTaskExecutor taskExecutor;

    @PostConstruct
    public void initialize() {
        log.info("初始化探针模块管理器...");

        if (modules == null || modules.isEmpty()) {
            log.warn("未发现任何探针模块");
            return;
        }

        // 注册所有可用模块（不管是否启用，由探针配置决定是否启动）
        for (ProbeModule module : modules) {
            moduleMap.put(module.getType(), module);
            log.info("注册探针模块: {} ({}) - 初始状态: {}",
                    module.getName(), module.getType(), module.isEnabled());
        }

        log.info("探针模块管理器初始化完成，已注册 {} 个模块", moduleMap.size());

        // 等待 ProbeSyncService 完成首次同步后同步模块
        if (probeSyncService != null) {
            // 延迟5秒执行首次同步，确保探针同步已完成
            if (taskExecutor != null) {
                taskExecutor.execute(() -> {
                    try {
                        Thread.sleep(5000);
                        syncModulesWithProbes();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("首次同步任务被中断", e);
                    }
                });
            } else {
                log.warn("TaskExecutor未配置，跳过首次同步");
            }
        }
    }

    /**
     * 根据同步的探针配置动态启动/停止模块
     * 定期执行（每30秒）以响应探针配置的变化
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void syncModulesWithProbes() {
        if (probeSyncService == null) {
            return;
        }

        try {
            // 获取当前同步的探针类型集合
            Set<ProbeType> currentTypes = new HashSet<>();
            for (ProbeSyncService.ProbeConfig probe : probeSyncService.getAllProbes()) {
                try {
                    ProbeType type = ProbeType.valueOf(probe.getType());
                    currentTypes.add(type);
                } catch (IllegalArgumentException e) {
                    log.warn("未知的探针类型: {}", probe.getType());
                }
            }

            // 检查是否有变化
            if (currentTypes.equals(lastSyncedTypes)) {
                return; // 无变化，无需处理
            }

            log.info("[模块同步] 探针类型发生变化: 上次={}, 当前={}", lastSyncedTypes, currentTypes);

            // 启动新出现的探针类型对应的模块
            for (ProbeType type : currentTypes) {
                if (!lastSyncedTypes.contains(type)) {
                    ProbeModule module = moduleMap.get(type);
                    if (module != null && module.getStatus() != ModuleStatus.RUNNING) {
                        try {
                            module.start();
                            log.info("[模块同步] 启动模块: {} ({})", module.getName(), type);
                        } catch (Exception e) {
                            log.error("[模块同步] 启动模块失败: {}", type, e);
                        }
                    }
                }
            }

            // 停止已删除的探针类型对应的模块（SYSTEM模块除外）
            for (ProbeType type : lastSyncedTypes) {
                if (!currentTypes.contains(type) && type != ProbeType.SYSTEM) {
                    ProbeModule module = moduleMap.get(type);
                    if (module != null && module.getStatus() == ModuleStatus.RUNNING) {
                        try {
                            module.stop();
                            log.info("[模块同步] 停止模块: {} ({})", module.getName(), type);
                        } catch (Exception e) {
                            log.error("[模块同步] 停止模块失败: {}", type, e);
                        }
                    }
                }
            }

            // 更新记录
            lastSyncedTypes.clear();
            lastSyncedTypes.addAll(currentTypes);

        } catch (Exception e) {
            log.error("[模块同步] 同步模块与探针配置失败", e);
        }
    }

    /**
     * 启动所有模块
     */
    public void startAll() throws Exception {
        if (running) {
            log.warn("模块管理器已在运行中");
            return;
        }

        log.info("启动所有探针模块...");

        // 连接 WebSocket
        if (wsClient != null) {
            wsClient.connect();
        }

        // 启动所有已注册的模块
        for (Map.Entry<ProbeType, ProbeModule> entry : moduleMap.entrySet()) {
            try {
                entry.getValue().start();
                log.info("模块启动成功: {}", entry.getKey());
            } catch (Exception e) {
                log.error("模块启动失败: {}", entry.getKey(), e);
            }
        }

        running = true;
        log.info("所有探针模块启动完成");
    }

    /**
     * 停止所有模块
     */
    public void stopAll() throws Exception {
        log.info("停止所有探针模块...");

        for (Map.Entry<ProbeType, ProbeModule> entry : moduleMap.entrySet()) {
            try {
                entry.getValue().stop();
                log.info("模块已停止: {}", entry.getKey());
            } catch (Exception e) {
                log.error("模块停止失败: {}", entry.getKey(), e);
            }
        }

        running = false;
        log.info("所有探针模块已停止");
    }

    /**
     * 分发命令到指定模块
     *
     * @param probeType 探针类型
     * @param command   命令
     * @param payload   命令载荷
     */
    public void dispatchCommand(ProbeType probeType, Command command, Object payload) {
        ProbeModule module = moduleMap.get(probeType);
        if (module == null) {
            log.warn("未找到模块: {}", probeType);
            return;
        }

        if (module.getStatus() != ModuleStatus.RUNNING) {
            log.warn("模块未运行，无法处理命令: {}", probeType);
            return;
        }

        try {
            module.onCommand(command, payload);
            log.debug("命令已分发到模块: {} - {}", probeType, command);
        } catch (Exception e) {
            log.error("处理命令失败: {} - {}", probeType, command, e);
        }
    }

    /**
     * 广播命令到所有模块
     *
     * @param command 命令
     * @param payload 命令载荷
     */
    public void broadcastCommand(Command command, Object payload) {
        log.info("广播命令到所有模块: {}", command);

        for (Map.Entry<ProbeType, ProbeModule> entry : moduleMap.entrySet()) {
            try {
                if (entry.getValue().getStatus() == ModuleStatus.RUNNING) {
                    entry.getValue().onCommand(command, payload);
                }
            } catch (Exception e) {
                log.error("广播命令失败: {} - {}", entry.getKey(), command, e);
            }
        }
    }

    /**
     * 获取模块状态
     *
     * @param probeType 探针类型
     * @return 模块状态
     */
    public ModuleStatus getModuleStatus(ProbeType probeType) {
        ProbeModule module = moduleMap.get(probeType);
        return module != null ? module.getStatus() : ModuleStatus.DISABLED;
    }

    /**
     * 获取所有模块状态
     *
     * @return 模块状态映射
     */
    public Map<ProbeType, ModuleStatus> getAllModuleStatus() {
        Map<ProbeType, ModuleStatus> statusMap = new HashMap<>();
        for (Map.Entry<ProbeType, ProbeModule> entry : moduleMap.entrySet()) {
            statusMap.put(entry.getKey(), entry.getValue().getStatus());
        }
        return statusMap;
    }

    /**
     * 检查管理器是否运行中
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取已注册的模块数量
     */
    public int getModuleCount() {
        return moduleMap.size();
    }

    /**
     * 获取指定类型的模块
     *
     * @param probeType 探针类型
     * @return 模块实例，如果不存在返回null
     */
    public ProbeModule getModule(ProbeType probeType) {
        return moduleMap.get(probeType);
    }

    /**
     * 获取所有已注册的模块
     *
     * @return 模块集合
     */
    public Collection<ProbeModule> getAllModules() {
        return Collections.unmodifiableCollection(moduleMap.values());
    }

    @PreDestroy
    public void destroy() {
        log.info("销毁模块管理器...");
        try {
            stopAll();
        } catch (Exception e) {
            log.error("停止模块失败", e);
        }
    }
}
