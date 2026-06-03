package com.lixin.probe.agent.cdc;

import com.lixin.probe.agent.config.DatabaseConfigManager;
import com.lixin.probe.agent.config.DatabaseConnectionConfig;
import com.lixin.probe.agent.plugin.api.CDCPlugin;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.lixin.probe.agent.service.CDCManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDC捕获调度器
 * 定时遍历所有cdcEnabled=true的数据库实例，调用对应CDC插件捕获变更事件，
 * 然后通过CDCManager批量上报到Admin管理端。
 */
@Component
public class CDCCaptureScheduler {

    private static final Logger log = LoggerFactory.getLogger(CDCCaptureScheduler.class);

    @Autowired
    private DatabaseConfigManager configManager;

    @Autowired
    private CDCManager cdcManager;

    private volatile boolean running = false;

    /** 每个实例的上次捕获位点（instanceId -> lastPosition） */
    private final Map<String, String> lastPositions = new ConcurrentHashMap<>();

    /** 每次捕获的最大事件数 */
    private static final int MAX_EVENTS_PER_CAPTURE = 100;

    /**
     * 启动CDC捕获调度
     */
    public void start() {
        if (running) {
            log.warn("[CDC] 捕获调度器已在运行中");
            return;
        }
        running = true;

        // 加载插件
        List<CDCPlugin> plugins = CDCPluginLoader.loadPlugins();
        log.info("[CDC] 捕获调度器启动，已加载 {} 个CDC插件", plugins.size());

        // 打印CDC监控的实例列表
        List<DatabaseConnectionConfig> cdcConfigs = getCdcEnabledConfigs();
        if (cdcConfigs.isEmpty()) {
            log.info("[CDC] 没有启用CDC的数据库实例");
        } else {
            for (DatabaseConnectionConfig db : cdcConfigs) {
                log.info("[CDC] CDC监控实例: instanceId={}, type={}, database={}, probeKey={}",
                        db.getInstanceId(), db.getDatabaseType(), db.getDatabaseName(), db.getProbeKey());
            }
        }
    }

    /**
     * 停止CDC捕获调度
     */
    public void stop() {
        running = false;
        lastPositions.clear();
        // 通过接口的 shutdown() 方法关闭所有CDC插件持有的连接
        for (CDCPlugin plugin : CDCPluginLoader.getAllPlugins()) {
            try {
                plugin.shutdown();
            } catch (Exception e) {
                log.warn("[CDC] 关闭插件 {} 异常: {}", plugin.getName(), e.getMessage());
            }
        }
        log.info("[CDC] 捕获调度器已停止");
    }

    /**
     * 定时捕获CDC事件（每10秒执行一次）
     * 并行对所有cdcEnabled=true的实例调用CDC插件捕获变更
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 30000)
    public void captureAll() {
        if (!running) return;

        List<DatabaseConnectionConfig> cdcEnabledConfigs = getCdcEnabledConfigs();
        if (cdcEnabledConfigs.isEmpty()) return;

        // 并行捕获所有实例的CDC事件，避免串行阻塞调度线程
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (DatabaseConnectionConfig dbConfig : cdcEnabledConfigs) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    captureForInstance(dbConfig);
                } catch (Exception e) {
                    log.error("[CDC] 捕获实例 {} 异常: {}", dbConfig.getInstanceId(), e.getMessage());
                }
            }));
        }

        // 等待所有捕获任务完成（最多等待15秒）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[CDC] 部分捕获任务超时或失败: {}", e.getMessage());
        }
    }

    /**
     * 对单个数据库实例执行CDC捕获
     */
    private void captureForInstance(DatabaseConnectionConfig dbConfig) {
        String instanceId = dbConfig.getInstanceId();
        String databaseType = dbConfig.getDatabaseType();

        CDCPlugin plugin = CDCPluginLoader.findPlugin(databaseType);
        if (plugin == null) {
            log.debug("[CDC] 无匹配插件: databaseType={}, instanceId={}", databaseType, instanceId);
            return;
        }

        Map<String, Object> config = buildPluginConfig(dbConfig);
        String fromPosition = lastPositions.get(instanceId);

        try {
            List<ProbeResponse.CDCEvent> events = plugin.captureChanges(
                    config, dbConfig.getDatabaseName(), null,
                    fromPosition, MAX_EVENTS_PER_CAPTURE
            ).get(10, java.util.concurrent.TimeUnit.SECONDS);

            if (events != null && !events.isEmpty()) {
                // 确保事件携带正确的数据库名称
                for (ProbeResponse.CDCEvent event : events) {
                    event.setDatabase(dbConfig.getDatabaseName());
                }

                cdcManager.enqueueEvents(events);

                String lastPos = events.get(events.size() - 1).getPosition();
                if (lastPos != null && !lastPos.isEmpty()) {
                    lastPositions.put(instanceId, lastPos);
                }

                log.debug("[CDC] 实例 {} 捕获到 {} 个事件", instanceId, events.size());
            }
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("[CDC] 捕获超时: instanceId={}", instanceId);
        } catch (Exception e) {
            log.error("[CDC] 捕获失败: instanceId={}, error={}", instanceId, e.getMessage());
        }
    }

    private Map<String, Object> buildPluginConfig(DatabaseConnectionConfig dbConfig) {
        Map<String, Object> config = new HashMap<>();
        config.put("host", dbConfig.getHost());
        config.put("port", dbConfig.getPort());
        config.put("database", dbConfig.getDatabaseName());
        config.put("username", dbConfig.getUsername());
        config.put("password", dbConfig.getPassword());
        return config;
    }

    private List<DatabaseConnectionConfig> getCdcEnabledConfigs() {
        return configManager.getEnabledDatabases().stream()
                .filter(db -> Boolean.TRUE.equals(db.getCdcEnabled()))
                .toList();
    }

    public boolean isRunning() {
        return running;
    }
}
