package com.lixin.probe.agent.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模块实例管理器
 * 负责管理每个探针对应的独立模块实例，实现资源隔离和多实例支持
 *
 * @author Claude Code
 * @since 2.0
 * @version 1.0
 */
@Component
public class ModuleInstanceManager {

    private static final Logger log = LoggerFactory.getLogger(ModuleInstanceManager.class);

    @Autowired
    private SystemMonitorModule systemMonitorModule;

    @Autowired
    private DatabaseModuleFactory databaseModuleFactory;

    @Autowired
    private FileModuleFactory fileModuleFactory;

    /**
     * 模块实例映射：probeKey -> ModuleInstance
     */
    private final Map<String, ModuleInstance> moduleInstances = new ConcurrentHashMap<>();

    /**
     * 按类型分组的实例：ProbeType -> Set<probeKey>
     */
    private final Map<ProbeType, Set<String>> instancesByType = new ConcurrentHashMap<>();

    /**
     * 资源统计
     */
    private final AtomicInteger totalInstances = new AtomicInteger(0);
    private final Map<ProbeType, AtomicInteger> instancesCountByType = new ConcurrentHashMap<>();

    /**
     * 模块实例内部类
     * 包装模块实例及其元数据
     */
    public static class ModuleInstance {
        private final String probeKey;
        private final ProbeType type;
        private final ProbeModule module;
        private final Date createdAt;
        private volatile Date lastUsedAt;
        private volatile boolean running;

        public ModuleInstance(String probeKey, ProbeType type, ProbeModule module) {
            this.probeKey = probeKey;
            this.type = type;
            this.module = module;
            this.createdAt = new Date();
            this.lastUsedAt = new Date();
            this.running = false;
        }

        public String getProbeKey() {
            return probeKey;
        }

        public ProbeType getType() {
            return type;
        }

        public ProbeModule getModule() {
            return module;
        }

        public Date getCreatedAt() {
            return createdAt;
        }

        public Date getLastUsedAt() {
            return lastUsedAt;
        }

        public void updateLastUsed() {
            this.lastUsedAt = new Date();
        }

        public boolean isRunning() {
            return running;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }
    }

    /**
     * 为指定探针创建或获取模块实例
     *
     * @param probeKey 探针标识
     * @param type 探针类型
     * @return 模块实例
     */
    public ModuleInstance getOrCreateModuleInstance(String probeKey, ProbeType type) {
        return moduleInstances.computeIfAbsent(probeKey, key -> {
            try {
                log.info("创建新模块实例: probeKey={}, type={}", probeKey, type);

                // 使用工厂创建模块实例
                ProbeModule module = createModule(probeKey, type);
                if (module == null) {
                    log.error("创建模块实例失败: probeKey={}, type={}", probeKey, type);
                    return null;
                }

                // 创建ModuleInstance包装
                ModuleInstance instance = new ModuleInstance(probeKey, type, module);

                // 更新统计
                totalInstances.incrementAndGet();
                instancesCountByType.computeIfAbsent(type, k -> new AtomicInteger(0)).incrementAndGet();
                instancesByType.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(probeKey);

                log.info("模块实例创建成功: probeKey={}, type={}, totalInstances={}",
                        probeKey, type, totalInstances.get());

                return instance;

            } catch (Exception e) {
                log.error("创建模块实例异常: probeKey={}, type={}", probeKey, type, e);
                return null;
            }
        });
    }

    /**
     * 使用工厂创建指定类型的模块实例
     */
    private ProbeModule createModule(String probeKey, ProbeType type) {
        try {
            switch (type) {
                case SYSTEM:
                    // 系统监控模块是单例，直接返回注入的实例
                    log.info("返回系统监控模块单例实例: probeKey={}", probeKey);
                    return systemMonitorModule;
                case DATABASE:
                    return databaseModuleFactory.createModule(probeKey);
                case FILE:
                    return fileModuleFactory.createModule(probeKey);
                default:
                    log.warn("未知的探针类型: {}", type);
                    return null;
            }
        } catch (Exception e) {
            log.error("创建模块失败: type={}, error={}", type, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 启动指定探针的模块实例
     *
     * @param probeKey 探针标识
     * @return 是否启动成功
     */
    public boolean startModuleInstance(String probeKey) {
        ModuleInstance instance = moduleInstances.get(probeKey);
        if (instance == null) {
            log.warn("模块实例不存在: probeKey={}", probeKey);
            return false;
        }

        try {
            if (instance.isRunning()) {
                log.debug("模块实例已在运行中: probeKey={}", probeKey);
                return true;
            }

            log.info("启动模块实例: probeKey={}, type={}", probeKey, instance.getType());
            instance.getModule().start();
            instance.setRunning(true);
            instance.updateLastUsed();

            log.info("模块实例启动成功: probeKey={}", probeKey);
            return true;

        } catch (Exception e) {
            log.error("启动模块实例失败: probeKey={}", probeKey, e);
            return false;
        }
    }

    /**
     * 停止指定探针的模块实例
     *
     * @param probeKey 探针标识
     * @return 是否停止成功
     */
    public boolean stopModuleInstance(String probeKey) {
        ModuleInstance instance = moduleInstances.get(probeKey);
        if (instance == null) {
            log.warn("模块实例不存在: probeKey={}", probeKey);
            return false;
        }

        try {
            if (!instance.isRunning()) {
                log.debug("模块实例已停止: probeKey={}", probeKey);
                return true;
            }

            log.info("停止模块实例: probeKey={}, type={}", probeKey, instance.getType());
            instance.getModule().stop();
            instance.setRunning(false);
            instance.updateLastUsed();

            log.info("模块实例停止成功: probeKey={}", probeKey);
            return true;

        } catch (Exception e) {
            log.error("停止模块实例失败: probeKey={}", probeKey, e);
            return false;
        }
    }

    /**
     * 销毁指定探针的模块实例
     *
     * @param probeKey 探针标识
     * @return 是否销毁成功
     */
    public boolean destroyModuleInstance(String probeKey) {
        ModuleInstance instance = moduleInstances.remove(probeKey);
        if (instance == null) {
            log.debug("模块实例不存在: probeKey={}", probeKey);
            return true;
        }

        try {
            // 如果模块正在运行，先停止它
            if (instance.isRunning()) {
                instance.getModule().stop();
            }

            // 从类型分组中移除
            Set<String> typeInstances = instancesByType.get(instance.getType());
            if (typeInstances != null) {
                typeInstances.remove(probeKey);
            }

            // 更新统计
            totalInstances.decrementAndGet();
            AtomicInteger count = instancesCountByType.get(instance.getType());
            if (count != null) {
                count.decrementAndGet();
            }

            log.info("模块实例已销毁: probeKey={}, type={}", probeKey, instance.getType());
            return true;

        } catch (Exception e) {
            log.error("销毁模块实例失败: probeKey={}", probeKey, e);
            return false;
        }
    }

    /**
     * 获取指定探针的模块实例
     *
     * @param probeKey 探针标识
     * @return 模块实例，不存在返回null
     */
    public ModuleInstance getModuleInstance(String probeKey) {
        ModuleInstance instance = moduleInstances.get(probeKey);
        if (instance != null) {
            instance.updateLastUsed();
        }
        return instance;
    }

    /**
     * 获取所有模块实例
     *
     * @return 模块实例集合
     */
    public Collection<ModuleInstance> getAllInstances() {
        return moduleInstances.values();
    }

    /**
     * 获取指定类型的所有实例
     *
     * @param type 探针类型
     * @return probeKey集合
     */
    public Set<String> getInstancesByType(ProbeType type) {
        return instancesByType.getOrDefault(type, Collections.emptySet());
    }

    /**
     * 获取实例统计信息
     *
     * @return 统计信息Map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalInstances", totalInstances.get());
        stats.put("instancesByType", new HashMap<>(instancesCountByType));

        Map<String, Integer> typeCounts = new HashMap<>();
        for (Map.Entry<ProbeType, AtomicInteger> entry : instancesCountByType.entrySet()) {
            typeCounts.put(entry.getKey().name(), entry.getValue().get());
        }
        stats.put("typeCounts", typeCounts);

        // 按类型统计实例数
        Map<String, Integer> typeInstanceCounts = new HashMap<>();
        for (Map.Entry<ProbeType, Set<String>> entry : instancesByType.entrySet()) {
            typeInstanceCounts.put(entry.getKey().name(), entry.getValue().size());
        }
        stats.put("typeInstanceCounts", typeInstanceCounts);

        return stats;
    }

    /**
     * 清理空闲的模块实例
     *
     * @param idleThresholdMs 空闲阈值（毫秒）
     * @return 清理的实例数量
     */
    public int cleanupIdleInstances(long idleThresholdMs) {
        int cleaned = 0;
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, ModuleInstance>> iterator = moduleInstances.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ModuleInstance> entry = iterator.next();
            ModuleInstance instance = entry.getValue();

            // 跳过正在运行的实例
            if (instance.isRunning()) {
                continue;
            }

            // 检查是否空闲
            long idleTime = now - instance.getLastUsedAt().getTime();
            if (idleTime > idleThresholdMs) {
                String probeKey = entry.getKey();
                log.info("清理空闲模块实例: probeKey={}, idleTime={}ms", probeKey, idleTime);

                if (destroyModuleInstance(probeKey)) {
                    cleaned++;
                }
            }
        }

        if (cleaned > 0) {
            log.info("清理完成: 清理了 {} 个空闲模块实例", cleaned);
        }

        return cleaned;
    }

    /**
     * 销毁所有模块实例（用于Agent关闭时）
     */
    public void destroyAll() {
        log.info("销毁所有模块实例...");

        int destroyed = 0;
        for (String probeKey : new ArrayList<>(moduleInstances.keySet())) {
            if (destroyModuleInstance(probeKey)) {
                destroyed++;
            }
        }

        log.info("所有模块实例已销毁: 总数={}", destroyed);
    }
}
