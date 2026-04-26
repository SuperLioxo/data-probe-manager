package com.lixin.probe.agent.probe;

import com.lixin.probe.agent.module.ModuleManager;
import com.lixin.probe.agent.module.ProbeType;
import com.lixin.probe.agent.sync.ProbeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 探针管理器
 * 负责管理探针的完整生命周期：启动、停止、重启、状态查询
 *
 * @author Claude Code
 * @since 1.0
 */
@Component
public class ProbeManager {

    private static final Logger log = LoggerFactory.getLogger(ProbeManager.class);

    @Autowired
    private ProbeScheduler scheduler;

    @Autowired
    private ProbeStateManager stateManager;

    @Autowired
    private ModuleManager moduleManager;

    @Autowired(required = false)
    private ProbeSyncService probeSyncService;

    /**
     * 启动所有探针
     *
     * @return 启动结果：成功数量，失败数量
     */
    public StartupResult startAllProbes() {
        log.info("启动所有探针...");

        int successCount = 0;
        int failCount = 0;
        List<String> failedProbes = new ArrayList<>();

        try {
            // 获取所有探针配置
            List<ProbeConfig> probes = getAllProbeConfigs();
            if (probes.isEmpty()) {
                log.warn("未找到任何探针配置");
                return new StartupResult(0, 0, Collections.emptyList());
            }

            // 启动每个探针
            for (ProbeConfig config : probes) {
                try {
                    boolean started = startProbe(config.probeKey, config.probeType);
                    if (started) {
                        successCount++;
                    } else {
                        failCount++;
                        failedProbes.add(config.probeKey);
                    }
                } catch (Exception e) {
                    log.error("启动探针异常: probeKey={}", config.probeKey, e);
                    failCount++;
                    failedProbes.add(config.probeKey);
                }
            }

            log.info("探针批量启动完成: 成功={}, 失败={}", successCount, failCount);
            if (!failedProbes.isEmpty()) {
                log.warn("启动失败的探针: {}", failedProbes);
            }

        } catch (Exception e) {
            log.error("启动所有探针失败", e);
        }

        return new StartupResult(successCount, failCount, failedProbes);
    }

    /**
     * 停止所有探针
     *
     * @return 停止结果：成功数量，失败数量
     */
    public ShutdownResult stopAllProbes() {
        log.info("停止所有探针...");

        int successCount = 0;
        int failCount = 0;
        List<String> failedProbes = new ArrayList<>();

        try {
            // 获取所有探针配置
            List<ProbeConfig> probes = getAllProbeConfigs();
            if (probes.isEmpty()) {
                log.warn("未找到任何探针配置");
                return new ShutdownResult(0, 0, Collections.emptyList());
            }

            // 停止每个探针
            for (ProbeConfig config : probes) {
                try {
                    boolean stopped = stopProbe(config.probeKey, config.probeType);
                    if (stopped) {
                        successCount++;
                    } else {
                        failCount++;
                        failedProbes.add(config.probeKey);
                    }
                } catch (Exception e) {
                    log.error("停止探针异常: probeKey={}", config.probeKey, e);
                    failCount++;
                    failedProbes.add(config.probeKey);
                }
            }

            log.info("探针批量停止完成: 成功={}, 失败={}", successCount, failCount);
            if (!failedProbes.isEmpty()) {
                log.warn("停止失败的探针: {}", failedProbes);
            }

        } catch (Exception e) {
            log.error("停止所有探针失败", e);
        }

        return new ShutdownResult(successCount, failCount, failedProbes);
    }

    /**
     * 启动指定探针
     *
     * @param probeKey  探针标识
     * @param probeType 探针类型
     * @return 是否启动成功
     */
    public boolean startProbe(String probeKey, ProbeType probeType) {
        log.info("启动探针: probeKey={}, type={}", probeKey, probeType);
        return scheduler.startProbe(probeKey, probeType);
    }

    /**
     * 停止指定探针
     *
     * @param probeKey  探针标识
     * @param probeType 探针类型
     * @return 是否停止成功
     */
    public boolean stopProbe(String probeKey, ProbeType probeType) {
        log.info("停止探针: probeKey={}, type={}", probeKey, probeType);
        return scheduler.stopProbe(probeKey, probeType);
    }

    /**
     * 重启指定探针
     *
     * @param probeKey  探针标识
     * @param probeType 探针类型
     * @return 是否重启成功
     */
    public boolean restartProbe(String probeKey, ProbeType probeType) {
        log.info("重启探针: probeKey={}, type={}", probeKey, probeType);
        return scheduler.restartProbe(probeKey, probeType);
    }

    /**
     * 获取探针状态
     *
     * @param probeKey 探针标识
     * @return 探针状态
     */
    public ProbeState getProbeState(String probeKey) {
        return stateManager.getState(probeKey);
    }

    /**
     * 获取所有探针状态
     *
     * @return 状态映射
     */
    public Map<String, ProbeState> getAllProbeStates() {
        return stateManager.getAllStates();
    }

    /**
     * 获取所有探针配置
     *
     * @return 探针配置列表
     */
    private List<ProbeConfig> getAllProbeConfigs() {
        List<ProbeConfig> configs = new ArrayList<>();

        if (probeSyncService == null) {
            return configs;
        }

        try {
            for (ProbeSyncService.ProbeConfig probe : probeSyncService.getAllProbes()) {
                ProbeType probeType = ProbeType.valueOf(probe.getType());
                configs.add(new ProbeConfig(probe.getProbeKey(), probeType));
            }
        } catch (Exception e) {
            log.error("获取探针配置失败", e);
        }

        return configs;
    }

    /**
     * 探针配置
     */
    public static class ProbeConfig {
        public final String probeKey;
        public final ProbeType probeType;

        public ProbeConfig(String probeKey, ProbeType probeType) {
            this.probeKey = probeKey;
            this.probeType = probeType;
        }
    }

    /**
     * 启动结果
     */
    public static class StartupResult {
        public final int successCount;
        public final int failCount;
        public final List<String> failedProbes;

        public StartupResult(int successCount, int failCount, List<String> failedProbes) {
            this.successCount = successCount;
            this.failCount = failCount;
            this.failedProbes = failedProbes;
        }

        public boolean isAllSuccess() {
            return failCount == 0;
        }
    }

    /**
     * 停止结果
     */
    public static class ShutdownResult {
        public final int successCount;
        public final int failCount;
        public final List<String> failedProbes;

        public ShutdownResult(int successCount, int failCount, List<String> failedProbes) {
            this.successCount = successCount;
            this.failCount = failCount;
            this.failedProbes = failedProbes;
        }

        public boolean isAllSuccess() {
            return failCount == 0;
        }
    }
}
