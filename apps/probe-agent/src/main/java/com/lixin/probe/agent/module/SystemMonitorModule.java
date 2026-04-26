package com.lixin.probe.agent.module;

import com.lixin.probe.agent.collector.SystemMetricsCollector;
import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.constant.Command;
import com.lixin.probe.agent.udp.MetricData;
import com.lixin.probe.agent.udp.UdpMetricSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 系统监控模块
 * 负责采集系统指标并通过 UDP 协议发送到服务端
 */
@Component
public class SystemMonitorModule implements ProbeModule {

    private static final Logger log = LoggerFactory.getLogger(SystemMonitorModule.class);

    @Autowired
    private AgentProperties agentProperties;

    @Autowired(required = false)
    private com.lixin.probe.agent.sync.ProbeSyncService probeSyncService;

    private SystemMetricsCollector collector;
    private UdpMetricSender sender;
    private ScheduledExecutorService scheduler;
    private String probeKey;
    private volatile boolean running = false;

    /**
     * 启动模块
     */
    @Override
    public void start() throws Exception {
        if (!isEnabled()) {
            log.info("系统监控模块未启用，跳过启动");
            return;
        }

        if (running) {
            log.warn("系统监控模块已在运行中");
            return;
        }

        try {
            // 1. 获取系统探针的probeKey
            if (probeSyncService != null) {
                var systemProbes = probeSyncService.getProbesByType("SYSTEM");
                if (!systemProbes.isEmpty()) {
                    probeKey = systemProbes.get(0).getProbeKey();
                    log.info("从探针同步服务获取到系统探针Key: {}", probeKey);
                }
            }

            if (probeKey == null || probeKey.isEmpty()) {
                throw new IllegalStateException("未找到系统探针的probeKey，无法启动系统监控模块");
            }

            // 2. 初始化采集器和发送器
            collector = new SystemMetricsCollector();
            sender = new UdpMetricSender(
                    agentProperties.getServer().getHost(),
                    agentProperties.getServer().getUdpPort(),
                    probeKey
            );

            // 3. 初始化调度器
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "system-monitor");
                thread.setDaemon(true);
                return thread;
            });

            // 4. 连接到服务端
            sender.connect();

            // 5. 启动定时采集任务
            long interval = agentProperties.getModules().getSystem().getCollectInterval();
            scheduler.scheduleAtFixedRate(
                    this::collectAndSend,
                    0,
                    interval,
                    TimeUnit.MILLISECONDS
            );

            running = true;
            log.info("系统监控模块启动成功，采集间隔: {}ms", interval);

        } catch (Exception e) {
            log.error("系统监控模块启动失败", e);
            throw e;
        }
    }

    /**
     * 停止模块
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        try {
            // 停止调度器
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }

            // 关闭发送器
            sender.close();

            log.info("系统监控模块已停止");

        } catch (Exception e) {
            log.error("停止系统监控模块失败", e);
        }
    }

    /**
     * 采集并发送指标数据
     */
    private void collectAndSend() {
        if (!running) {
            log.warn("系统监控模块未运行，跳过采集");
            return;
        }

        try {
            log.info("开始采集系统指标...");

            // 1. 采集系统指标
            List<MetricData> metrics = collector.collectMetrics();

            if (metrics == null || metrics.isEmpty()) {
                log.warn("未采集到任何指标数据");
                return;
            }

            log.info("采集到 {} 个系统指标", metrics.size());

            // 2. 发送指标数据
            boolean success = sender.sendMetrics(metrics);

            if (!success) {
                log.warn("发送指标数据失败");
            } else {
                log.info("成功发送 {} 个系统指标", metrics.size());
            }

        } catch (Exception e) {
            log.error("采集并发送指标数据失败", e);
        }
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isEnabled() {
        return agentProperties.getModules().getSystem().getEnabled();
    }

    @Override
    public String getName() {
        return "SystemMonitor";
    }

    @Override
    public ModuleStatus getStatus() {
        return running ? ModuleStatus.RUNNING : ModuleStatus.STOPPED;
    }

    @Override
    public ProbeType getType() {
        return ProbeType.SYSTEM;
    }

    @Override
    public void onCommand(Command command, Object payload) {
        log.info("收到系统监控模块命令: {}", command);

        switch (command) {
            case START:
                try {
                    if (!running) {
                        start();
                    }
                } catch (Exception e) {
                    log.error("启动系统监控模块失败", e);
                }
                break;

            case STOP:
                stop();
                break;

            default:
                log.warn("系统监控模块不支持命令: {}", command);
        }
    }
}
