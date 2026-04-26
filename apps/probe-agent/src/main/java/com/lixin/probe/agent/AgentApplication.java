package com.lixin.probe.agent;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.discovery.ProbeDiscoveryClient;
import com.lixin.probe.agent.module.ModuleManager;
import com.lixin.probe.agent.module.ProbeType;
import com.lixin.probe.agent.probe.ProbeManager;
import com.lixin.probe.agent.probe.ProbeStateManager;
import com.lixin.probe.agent.sync.ProbeSyncService;
import com.lixin.probe.agent.websocket.WebSocketClientHandler;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * 探针代理应用主入口
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AgentApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentApplication.class);
    @Autowired(required = false)
    private ModuleManager moduleManager;

    @Autowired(required = false)
    private ProbeDiscoveryClient discoveryClient;

    @Autowired(required = false)
    private ProbeSyncService probeSyncService;

    @Autowired(required = false)
    private WebSocketClientHandler webSocketClientHandler;

    @Autowired(required = false)
    private ProbeManager probeManager;

    @Autowired(required = false)
    private ProbeStateManager probeStateManager;

    // 用于保持应用运行的CountDownLatch
    private CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        // 添加JVM关闭钩子，确保优雅关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n正在关闭探针Agent...");
        }));

        SpringApplication.run(AgentApplication.class, args);
    }

    /**
     * 恢复之前运行中的探针
     */
    private void recoverProbes() {
        if (probeStateManager == null || probeManager == null) {
            log.info("[探针恢复] 状态管理器或探针管理器不可用，跳过恢复");
            return;
        }

        Map<String, String> toRecover = probeStateManager.getProbesToRecover();
        if (toRecover.isEmpty()) {
            log.info("[探针恢复] 无需恢复的探针");
            return;
        }

        log.info("[探针恢复] 开始恢复 {} 个探针...", toRecover.size());
        int recovered = 0;
        for (Map.Entry<String, String> entry : toRecover.entrySet()) {
            String probeKey = entry.getKey();
            String typeStr = entry.getValue();
            try {
                ProbeType type = ProbeType.valueOf(typeStr);
                boolean ok = probeManager.startProbe(probeKey, type);
                if (ok) {
                    recovered++;
                    log.info("[探针恢复] 恢复成功: probeKey={}, type={}", probeKey, type);
                } else {
                    log.warn("[探针恢复] 恢复失败: probeKey={}", probeKey);
                }
            } catch (Exception e) {
                log.warn("[探针恢复] 恢复异常: probeKey={}, error={}", probeKey, e.getMessage());
            }
        }
        log.info("[探针恢复] 恢复完成: {}/{} 成功", recovered, toRecover.size());
    }

    /**
     * 启动后初始化
     */
    @Bean
    public CommandLineRunner initialize(AgentProperties properties) {
        return args -> {
            log.info("正在启动探针代理...");
            log.info("探针编码: {}", properties.getCode());
            log.info("服务端地址: {}:{}", properties.getServer().getHost(), properties.getServer().getPort());

            try {
                // UDP自动发现和注册（如果启用）
                if (properties.getStartup().getAutoRegister() && discoveryClient != null) {
                    log.info("开始UDP自动发现和注册...");
                    int retryTimes = properties.getStartup().getRegisterRetryTimes();
                    long retryInterval = properties.getStartup().getRegisterRetryInterval();

                    for (int i = 0; i < retryTimes; i++) {
                        var response = discoveryClient.discover();
                        if (response != null && response.getCode() == 200) {
                            log.info("UDP发现成功！已创建或找到 {} 个探针",
                                response.getProbes() != null ? response.getProbes().size() : 0);
                            log.info("WebSocket连接URL: {}", response.getWebsocketUrl());
                            break;
                        } else {
                            if (i < retryTimes - 1) {
                                log.warn("UDP发现失败，{} ms后重试 ({}/{})...",
                                    retryInterval, i + 1, retryTimes);
                                Thread.sleep(retryInterval);
                            }
                        }
                    }
                }

                // 初始化探针同步服务（动态探针配置）
                if (probeSyncService != null) {
                    log.info("初始化探针同步服务...");
                    probeSyncService.initialize();
                }

                // 启动所有模块（包括系统监控、数据库、文件等模块）
                // 注意：系统监控模块现在是Spring管理的组件，会自动注册到ModuleManager
                if (moduleManager != null && moduleManager.getModuleCount() > 0) {
                    log.info("通过ModuleManager启动 {} 个模块", moduleManager.getModuleCount());
                    moduleManager.startAll();
                } else {
                    log.info("没有模块需要启动");
                }

                // WebSocket连接已由ModuleManager.startAll()中的wsClient.connect()处理，不再重复连接

                // 恢复之前运行中的探针
                recoverProbes();

                // 添加关闭钩子
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    log.info("收到关闭信号，正在停止...");
                    shutdownLatch.countDown();
                }));

                log.info("探针代理启动完成！");

                // 保持应用运行，等待关闭信号
                log.info("探针Agent正在运行中，按 Ctrl+C 或发送终止信号停止...");
                shutdownLatch.await();

            } catch (Exception e) {
                log.error("探针代理启动失败", e);
                System.exit(1);
            }
        };
    }

    /**
     * 关闭钩子
     */
    @PreDestroy
    public void shutdown() {
        log.info("正在关闭探针代理...");

        if (moduleManager != null) {
            try {
                moduleManager.stopAll();
            } catch (Exception e) {
                log.error("停止模块管理器失败", e);
            }
        }

        log.info("探针代理已关闭");
    }
}
