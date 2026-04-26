package com.lixin.probe.agent.controller;

import com.lixin.probe.agent.plugin.loader.SpiPluginLoader;
import com.lixin.probe.agent.support.MinioSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 提供应用健康状态、就绪状态、版本信息等端点
 *
 * @author probe-agent
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    @Autowired(required = false)
    private BuildProperties buildProperties;

    @Autowired
    private SpiPluginLoader pluginLoader;

    @Autowired
    private MinioSupport minioSupport;

    /**
     * 应用启动时间
     */
    private static final Instant START_TIME = Instant.now();

    /**
     * 健康检查端点
     * GET /api/health
     */
    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        Map<String, Object> components = new LinkedHashMap<>();

        // 检查 JVM
        components.put("jvm", checkJvm());

        // 检查插件加载器
        components.put("plugins", checkPlugins());

        // 检查 MinIO
        components.put("minio", checkMinio());

        // 检查磁盘空间
        components.put("diskSpace", checkDiskSpace());

        // 检查内存
        components.put("memory", checkMemory());

        health.put("components", components);

        return health;
    }

    /**
     * 就绪检查端点
     * GET /api/health/ready
     */
    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean isReady = true;

        // 检查关键组件是否就绪
        boolean pluginsReady = pluginLoader.getAllPlugins().size() > 0;
        if (!pluginsReady) {
            isReady = false;
        }

        result.put("status", isReady ? "READY" : "NOT_READY");
        result.put("timestamp", System.currentTimeMillis());
        result.put("pluginsLoaded", pluginsReady);

        return result;
    }

    /**
     * 存活检查端点
     * GET /api/health/live
     */
    @GetMapping("/live")
    public Map<String, Object> live() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ALIVE");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 版本信息端点
     * GET /api/health/info
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();

        // 应用信息
        info.put("application", "probe-agent");
        info.put("description", "数据探针代理服务");
        info.put("version", buildProperties != null ? buildProperties.getVersion() : "1.0.0");

        // 构建信息
        if (buildProperties != null) {
            Map<String, String> build = new HashMap<>();
            build.put("name", buildProperties.getName());
            build.put("artifact", buildProperties.getArtifact());
            build.put("group", buildProperties.getGroup());
            build.put("time", buildProperties.getTime() != null ? buildProperties.getTime().toString() : "unknown");
            info.put("build", build);
        }

        // Java 信息
        Map<String, Object> java = new HashMap<>();
        java.put("version", System.getProperty("java.version"));
        java.put("vendor", System.getProperty("java.vendor"));
        java.put("home", System.getProperty("java.home"));
        info.put("java", java);

        // JVM 信息
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> jvm = new HashMap<>();
        jvm.put("processors", runtime.availableProcessors());
        jvm.put("jvmName", System.getProperty("java.vm.name"));
        jvm.put("jvmVersion", System.getProperty("java.vm.version"));
        info.put("jvm", jvm);

        // 运行时间
        long uptime = Duration.between(START_TIME, Instant.now()).toMillis();
        info.put("uptime", formatDuration(uptime));
        info.put("startTime", START_TIME.toEpochMilli());

        return info;
    }

    /**
     * 详细指标端点
     * GET /api/health/metrics
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // 运行时指标
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> runtimeMetrics = new HashMap<>();
        runtimeMetrics.put("maxMemory", runtime.maxMemory());
        runtimeMetrics.put("totalMemory", runtime.totalMemory());
        runtimeMetrics.put("freeMemory", runtime.freeMemory());
        runtimeMetrics.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        runtimeMetrics.put("availableProcessors", runtime.availableProcessors());
        metrics.put("runtime", runtimeMetrics);

        // JVM 内存指标
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Map<String, Object> heapMetrics = new HashMap<>();
        heapMetrics.put("heapMax", memoryBean.getHeapMemoryUsage().getMax());
        heapMetrics.put("heapUsed", memoryBean.getHeapMemoryUsage().getUsed());
        heapMetrics.put("heapCommitted", memoryBean.getHeapMemoryUsage().getCommitted());
        metrics.put("heap", heapMetrics);

        // 线程指标
        Map<String, Object> threadMetrics = new HashMap<>();
        threadMetrics.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        threadMetrics.put("peakThreadCount", ManagementFactory.getThreadMXBean().getPeakThreadCount());
        threadMetrics.put("totalStartedThreadCount", ManagementFactory.getThreadMXBean().getTotalStartedThreadCount());
        metrics.put("threads", threadMetrics);

        // 类加载指标
        Map<String, Object> classMetrics = new HashMap<>();
        classMetrics.put("loadedClassCount", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
        classMetrics.put("totalLoadedClassCount", ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount());
        classMetrics.put("unloadedClassCount", ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount());
        metrics.put("classes", classMetrics);

        // 运行时指标
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> runtimeBeanMetrics = new HashMap<>();
        runtimeBeanMetrics.put("uptime", runtimeBean.getUptime());
        runtimeBeanMetrics.put("startTime", runtimeBean.getStartTime());
        metrics.put("runtimeBean", runtimeBeanMetrics);

        // 插件统计
        Map<String, Object> pluginMetrics = new HashMap<>();
        pluginMetrics.put("totalPlugins", pluginLoader.getAllPlugins().size());
        pluginMetrics.put("supportedTypes", pluginLoader.getSupportedTypes());
        pluginMetrics.put("statistics", pluginLoader.getStatistics());
        metrics.put("plugins", pluginMetrics);

        // MinIO 状态
        Map<String, Object> minioMetrics = new HashMap<>();
        minioMetrics.put("available", minioSupport.isAvailable());
        metrics.put("minio", minioMetrics);

        return metrics;
    }

    /**
     * 检查 JVM 状态
     */
    private Map<String, Object> checkJvm() {
        Map<String, Object> jvm = new HashMap<>();
        jvm.put("status", "UP");

        try {
            // 尝试获取 JVM 统计信息
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            jvm.put("uptime", runtimeBean.getUptime());
        } catch (Exception e) {
            jvm.put("status", "DOWN");
            jvm.put("error", e.getMessage());
        }

        return jvm;
    }

    /**
     * 检查插件加载器状态
     */
    private Map<String, Object> checkPlugins() {
        Map<String, Object> plugins = new HashMap<>();

        int pluginCount = pluginLoader.getAllPlugins().size();
        if (pluginCount > 0) {
            plugins.put("status", "UP");
            plugins.put("count", pluginCount);
            plugins.put("types", pluginLoader.getSupportedTypes());
        } else {
            plugins.put("status", "DOWN");
            plugins.put("error", "No plugins loaded");
        }

        return plugins;
    }

    /**
     * 检查 MinIO 状态
     */
    private Map<String, Object> checkMinio() {
        Map<String, Object> minio = new HashMap<>();

        if (minioSupport.isAvailable()) {
            minio.put("status", "UP");
        } else {
            minio.put("status", "DOWN");
            minio.put("error", "MinIO service not available");
        }

        return minio;
    }

    /**
     * 检查磁盘空间
     */
    private Map<String, Object> checkDiskSpace() {
        Map<String, Object> disk = new HashMap<>();

        try {
            java.io.File root = new java.io.File("/");
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usableSpace = root.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;

            double threshold = 0.9; // 90% 阈值

            if (freeSpace > 0 && (double) usedSpace / totalSpace < threshold) {
                disk.put("status", "UP");
            } else {
                disk.put("status", "DOWN");
                disk.put("error", "Disk space below threshold");
            }

            disk.put("total", totalSpace);
            disk.put("free", freeSpace);
            disk.put("usable", usableSpace);
            disk.put("used", usedSpace);
            disk.put("usagePercent", (double) usedSpace / totalSpace * 100);
        } catch (Exception e) {
            disk.put("status", "DOWN");
            disk.put("error", e.getMessage());
        }

        return disk;
    }

    /**
     * 检查内存状态
     */
    private Map<String, Object> checkMemory() {
        Map<String, Object> memory = new HashMap<>();

        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMax = memoryBean.getHeapMemoryUsage().getMax();

            double usage = (double) heapUsed / heapMax;
            double threshold = 0.9; // 90% 阈值

            if (usage < threshold) {
                memory.put("status", "UP");
            } else {
                memory.put("status", "DOWN");
                memory.put("error", "Heap memory usage above threshold");
            }

            memory.put("heapUsed", heapUsed);
            memory.put("heapMax", heapMax);
            memory.put("heapUsagePercent", usage * 100);
        } catch (Exception e) {
            memory.put("status", "DOWN");
            memory.put("error", e.getMessage());
        }

        return memory;
    }

    /**
     * 格式化时间长度
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d days, %d hours, %d minutes", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d minutes, %d seconds", minutes, seconds % 60);
        } else {
            return String.format("%d seconds", seconds);
        }
    }
}
