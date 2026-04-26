package com.lixin.probe.agent.module;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.constant.Command;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.lixin.probe.agent.service.DatabaseService;
import com.lixin.probe.agent.websocket.WebSocketClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 数据库探针模块
 * 负责数据库元数据和数据量的采集与上报
 */
@Component
public class DatabaseModule implements ProbeModule {

    private static final Logger log = LoggerFactory.getLogger(DatabaseModule.class);

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private WebSocketClientHandler wsClient;

    private volatile boolean running = false;

    @Override
    public String getName() {
        return "Database Metadata Module";
    }

    @Override
    public ProbeType getType() {
        return ProbeType.DATABASE;
    }

    @Override
    public boolean isEnabled() {
        return agentProperties.getModules().getDatabase().getEnabled();
    }

    @Override
    public void start() throws Exception {
        if (!isEnabled()) {
            log.info("数据库模块未启用");
            return;
        }

        if (running) {
            log.warn("数据库模块已在运行中");
            return;
        }

        log.info("启动数据库元数据探针模块...");

        // 测试数据库连接
        boolean connected = databaseService.testConnections();
        if (!connected) {
            log.warn("数据库连接测试失败，但模块将继续启动");
        }

        running = true;
        log.info("数据库元数据探针模块启动成功");

        // 延迟5秒后自动采集一次元数据（用于测试）
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                if (running) {
                    log.info("自动触发数据库元数据采集...");
                    collectAndReportMetadata();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Override
    public void stop() throws Exception {
        log.info("停止数据库元数据探针模块...");
        running = false;
    }

    @Override
    public ModuleStatus getStatus() {
        return running ? ModuleStatus.RUNNING : ModuleStatus.STOPPED;
    }

    /**
     * 采集并上报元数据
     */
    public void collectAndReportMetadata() {
        if (!running) {
            log.warn("数据库模块未运行，无法采集元数据");
            return;
        }

        log.info("开始采集数据库元数据...");

        try {
            // 异步采集元数据
            CompletableFuture<ProbeResponse.Metadata> future =
                    databaseService.collectMetadataAsync();

            future.thenAccept(metadata -> {
                if (metadata != null && wsClient.isConnected()) {
                    // 通过 WebSocket 上报
                    wsClient.sendMetadata(metadata);
                    log.info("数据库元数据上报成功");
                } else {
                    log.warn("WebSocket 未连接，无法上报元数据");
                }
            }).exceptionally(e -> {
                log.error("采集数据库元数据失败", e);
                return null;
            });

        } catch (Exception e) {
            log.error("启动元数据采集任务失败", e);
        }
    }

    /**
     * 采集并上报数据量信息
     */
    public void collectAndReportDataSize() {
        if (!running) {
            log.warn("数据库模块未运行，无法采集数据量");
            return;
        }

        log.info("开始采集数据库数据量信息...");

        try {
            // 异步采集数据量
            CompletableFuture<ProbeResponse.DataSize> future =
                    databaseService.collectDataSizeAsync();

            future.thenAccept(dataSize -> {
                if (dataSize != null && wsClient.isConnected()) {
                    // 通过 WebSocket 上报
                    wsClient.sendDataSize(dataSize);
                    log.info("数据库数据量信息上报成功");
                } else {
                    log.warn("WebSocket 未连接，无法上报数据量信息");
                }
            }).exceptionally(e -> {
                log.error("采集数据库数据量失败", e);
                return null;
            });

        } catch (Exception e) {
            log.error("启动数据量采集任务失败", e);
        }
    }

    /**
     * 执行完整的数据采集（元数据 + 数据量）
     */
    public void executeFullCollection() {
        log.info("开始执行完整的数据库数据采集...");

        collectAndReportMetadata();

        // 延迟1秒后采集数据量，避免同时采集造成压力
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        collectAndReportDataSize();

        log.info("完整数据库数据采集任务已提交");
    }

    @Override
    public void onCommand(Command command, Object payload) {
        log.info("收到数据库模块命令: {}", command);

        switch (command) {
            case PROBE:
                // 采集元数据
                collectAndReportMetadata();
                break;

            case EXTRACT:
                // 采集数据量
                collectAndReportDataSize();
                break;

            case UPDATE_DB_CONFIG:
                // 更新数据库配置
                handleUpdateDbConfig(payload);
                break;

            default:
                log.warn("数据库模块不支持命令: {}", command);
        }
    }

    /**
     * 处理更新数据库配置命令
     */
    private void handleUpdateDbConfig(Object payload) {
        log.info("处理UPDATE_DB_CONFIG命令");

        try {
            if (!(payload instanceof Map)) {
                log.warn("UPDATE_DB_CONFIG命令payload格式错误");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) payload;

            // 提取配置信息
            String probeKey = (String) configMap.get("probeKey");
            String databaseType = (String) configMap.get("databaseType");
            String host = (String) configMap.get("host");
            Integer port = (Integer) configMap.get("port");
            String databaseName = (String) configMap.get("name");
            String username = (String) configMap.get("username");
            String password = (String) configMap.get("password");

            @SuppressWarnings("unchecked")
            List<String> schemas = (List<String>) configMap.get("schemas");

            if (probeKey == null || databaseType == null || host == null ||
                port == null || databaseName == null || username == null) {
                log.warn("UPDATE_DB_CONFIG配置参数不完整");
                return;
            }

            // 构建DatabaseConfig对象
            AgentProperties.DatabaseConfig dbConfig = new AgentProperties.DatabaseConfig();
            dbConfig.setType(databaseType);
            dbConfig.setHost(host);
            dbConfig.setPort(port);
            dbConfig.setName(databaseName);
            dbConfig.setProbeKey(probeKey);  // 设置probeKey
            dbConfig.setUsername(username);
            dbConfig.setPassword(password);
            dbConfig.setSchemas(schemas);

            // 添加或更新配置
            boolean success = databaseService.addOrUpdateDatabaseConfig(probeKey, dbConfig);

            if (success) {
                log.info("数据库配置更新成功: probeKey={}", probeKey);

                // 发送成功响应
                sendCommandResponse(probeKey, "UPDATE_DB_CONFIG", "SUCCESS", "配置已更新");
            } else {
                log.warn("数据库配置更新失败: probeKey={}", probeKey);

                // 发送失败响应
                sendCommandResponse(probeKey, "UPDATE_DB_CONFIG", "FAILED", "配置更新失败");
            }

        } catch (Exception e) {
            log.error("处理UPDATE_DB_CONFIG命令失败", e);
        }
    }

    /**
     * 发送命令响应
     */
    private void sendCommandResponse(String probeKey, String command, String status, String message) {
        try {
            // TODO: 实现WebSocket命令响应
            log.info("命令响应: probeKey={}, command={}, status={}, message={}",
                probeKey, command, status, message);
        } catch (Exception e) {
            log.error("处理命令响应失败", e);
        }
    }

    /**
     * 定期采集元数据（每30分钟）
     * 使用fixedDelay确保上一次采集完成后再等待指定时间
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 60 * 1000)
    public void scheduledMetadataCollection() {
        if (!running) {
            return;
        }
        log.info("[定期任务] 开始执行元数据采集...");
        collectAndReportMetadata();
    }

    /**
     * 定期采集数据量（每5分钟）
     * 使用fixedDelay确保上一次采集完成后再等待指定时间
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 120 * 1000)
    public void scheduledDataSizeCollection() {
        if (!running) {
            return;
        }
        log.info("[定期任务] 开始执行数据量采集...");
        collectAndReportDataSize();
    }
}
