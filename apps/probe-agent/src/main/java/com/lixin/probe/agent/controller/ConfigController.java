package com.lixin.probe.agent.controller;

import com.lixin.probe.agent.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
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
 * 配置管理控制器
 * 提供配置查看、系统信息查询等功能
 *
 * @author probe-agent
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);
    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private Environment environment;

    /**
     * 应用启动时间
     */
    private static final Instant START_TIME = Instant.now();

    /**
     * 获取应用配置信息
     * GET /api/config/application
     */
    @GetMapping("/application")
    public Map<String, Object> getApplicationConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "应用配置信息 / Application configuration");

        Map<String, Object> config = new LinkedHashMap<>();

        // 探针配置
        Map<String, Object> agentConfig = new HashMap<>();
        agentConfig.put("code", agentProperties.getCode());
        agentConfig.put("keyConfigured", agentProperties.getKey() != null && !agentProperties.getKey().isEmpty());
        config.put("agent", agentConfig);

        // 服务器配置
        Map<String, Object> serverConfig = new HashMap<>();
        serverConfig.put("host", agentProperties.getServer().getHost());
        serverConfig.put("port", agentProperties.getServer().getPort());
        serverConfig.put("udpPort", agentProperties.getServer().getUdpPort());
        serverConfig.put("discoveryPort", agentProperties.getServer().getDiscoveryPort());
        serverConfig.put("wsMetaUrl", agentProperties.getServer().getWsMetaUrl());
        serverConfig.put("wsFileUrl", agentProperties.getServer().getWsFileUrl());
        config.put("server", serverConfig);

        // 模块配置
        Map<String, Object> modulesConfig = new HashMap<>();
        modulesConfig.put("systemEnabled", agentProperties.getModules().getSystem().getEnabled());
        modulesConfig.put("databaseEnabled", agentProperties.getModules().getDatabase().getEnabled());
        modulesConfig.put("fileEnabled", agentProperties.getModules().getFile().getEnabled());
        // 网络指标已合并到系统探针中
        modulesConfig.put("networkInSystem", true);
        config.put("modules", modulesConfig);

        // Spring Profile
        String[] activeProfiles = environment.getActiveProfiles();
        config.put("profiles", activeProfiles.length > 0 ? activeProfiles : new String[]{"default"});

        result.put("data", config);

        return result;
    }

    /**
     * 获取系统环境信息
     * GET /api/config/environment
     */
    @GetMapping("/environment")
    public Map<String, Object> getEnvironment() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "系统环境信息 / System environment");

        Map<String, Object> env = new LinkedHashMap<>();

        // Java 版本
        env.put("java.version", System.getProperty("java.version"));
        env.put("java.vendor", System.getProperty("java.vendor"));
        env.put("java.home", System.getProperty("java.home"));

        // 操作系统
        env.put("os.name", System.getProperty("os.name"));
        env.put("os.version", System.getProperty("os.version"));
        env.put("os.arch", System.getProperty("os.arch"));

        // 用户信息
        env.put("user.name", System.getProperty("user.name"));
        env.put("user.home", System.getProperty("user.home"));
        env.put("user.dir", System.getProperty("user.dir"));

        // JVM 信息
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> jvm = new HashMap<>();
        jvm.put("name", runtimeBean.getVmName());
        jvm.put("version", runtimeBean.getVmVersion());
        jvm.put("vendor", runtimeBean.getVmVendor());
        jvm.put("uptime", formatDuration(runtimeBean.getUptime()));
        jvm.put("startTime", runtimeBean.getStartTime());
        jvm.put("inputArguments", runtimeBean.getInputArguments());
        env.put("jvm", jvm);

        // 内存信息
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Map<String, Object> memory = new HashMap<>();
        memory.put("heap.max", formatBytes(memoryBean.getHeapMemoryUsage().getMax()));
        memory.put("heap.used", formatBytes(memoryBean.getHeapMemoryUsage().getUsed()));
        memory.put("heap.committed", formatBytes(memoryBean.getHeapMemoryUsage().getCommitted()));
        memory.put("non-heap.max", formatBytes(memoryBean.getNonHeapMemoryUsage().getMax()));
        memory.put("non-heap.used", formatBytes(memoryBean.getNonHeapMemoryUsage().getUsed()));
        env.put("memory", memory);

        // 运行时信息
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> runtimeInfo = new HashMap<>();
        runtimeInfo.put("processors", runtime.availableProcessors());
        runtimeInfo.put("maxMemory", formatBytes(runtime.maxMemory()));
        runtimeInfo.put("totalMemory", formatBytes(runtime.totalMemory()));
        runtimeInfo.put("freeMemory", formatBytes(runtime.freeMemory()));
        env.put("runtime", runtimeInfo);

        // 应用信息
        Map<String, Object> app = new HashMap<>();
        app.put("uptime", formatDuration(Duration.between(START_TIME, Instant.now()).toMillis()));
        app.put("startTime", START_TIME.toEpochMilli());
        env.put("application", app);

        result.put("data", env);

        return result;
    }

    /**
     * 获取系统属性
     * GET /api/config/system-properties
     */
    @GetMapping("/system-properties")
    public Map<String, Object> getSystemProperties() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "系统属性 / System properties");

        Map<String, Object> props = new LinkedHashMap<>();

        // 常用系统属性
        props.put("java.version", System.getProperty("java.version"));
        props.put("java.home", System.getProperty("java.home"));
        props.put("java.class.path", System.getProperty("java.class.path"));
        props.put("java.library.path", System.getProperty("java.library.path"));

        props.put("os.name", System.getProperty("os.name"));
        props.put("os.arch", System.getProperty("os.arch"));
        props.put("os.version", System.getProperty("os.version"));

        props.put("user.name", System.getProperty("user.name"));
        props.put("user.home", System.getProperty("user.home"));
        props.put("user.dir", System.getProperty("user.dir"));
        props.put("user.language", System.getProperty("user.language"));
        props.put("user.timezone", System.getProperty("user.timezone"));

        props.put("file.separator", System.getProperty("file.separator"));
        props.put("path.separator", System.getProperty("path.separator"));
        props.put("line.separator", System.getProperty("line.separator"));

        props.put("java.tmpdir", System.getProperty("java.io.tmpdir"));
        props.put("java.specification.version", System.getProperty("java.specification.version"));

        result.put("data", props);

        return result;
    }

    /**
     * 获取环境变量（敏感信息已脱敏）
     * GET /api/config/env-vars
     */
    @GetMapping("/env-vars")
    public Map<String, Object> getEnvironmentVariables() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "环境变量（敏感信息已脱敏）/ Environment variables (sensitive data masked)");

        Map<String, String> envVars = new LinkedHashMap<>();
        Map<String, String> systemEnv = System.getenv();

        // 只返回非敏感的环境变量
        systemEnv.forEach((key, value) -> {
            if (!isSensitiveEnvVar(key)) {
                envVars.put(key, value);
            } else {
                envVars.put(key, "***");
            }
        });

        result.put("data", envVars);
        result.put("total", systemEnv.size());

        return result;
    }

    /**
     * 判断是否为敏感环境变量
     */
    private boolean isSensitiveEnvVar(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") ||
               lowerKey.contains("secret") ||
               lowerKey.contains("key") ||
               lowerKey.contains("token") ||
               lowerKey.contains("credential") ||
               lowerKey.contains("auth");
    }

    /**
     * 格式化字节大小
     */
    private String formatBytes(long bytes) {
        if (bytes == 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        double size = bytes;
        while (size >= 1024 && idx < units.length - 1) {
            size /= 1024;
            idx++;
        }
        return String.format("%.2f %s", size, units[idx]);
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
            return String.format("%d天%d小时%d分钟", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds % 60);
        } else {
            return seconds + "秒";
        }
    }
}
