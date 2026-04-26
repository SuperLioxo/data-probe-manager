package com.lixin.probe.agent.config;

import com.lixin.probe.agent.connection.ConnectionPoolManager;
import com.lixin.probe.agent.plugin.loader.SpiPluginLoader;
import com.lixin.probe.agent.support.MinioSupport;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动配置验证器
 * 在应用启动时验证关键配置的正确性
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Component
public class StartupConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);
    @Autowired
    private AgentProperties agentProperties;

    @Autowired(required = false)
    private MinioConfig minioConfig;

    @Autowired
    private MinioSupport minioSupport;

    @Autowired
    private SpiPluginLoader pluginLoader;

    @Autowired(required = false)
    private ConnectionPoolManager connectionPoolManager;

    /**
     * 验证配置
     */
    @PostConstruct
    public void validate() {
        log.info("========================================");
        log.info("开始验证启动配置...");
        log.info("========================================");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. 验证探针基础配置
        validateAgentConfig(errors, warnings);

        // 2. 验证MinIO配置
        validateMinioConfig(errors, warnings);

        // 3. 验证插件配置
        validatePluginConfig(errors, warnings);

        // 4. 验证文件路径配置
        validatePathConfig(errors, warnings);

        // 5. 验证系统资源
        validateSystemResources(errors, warnings);

        // 输出验证结果
        if (errors.isEmpty()) {
            log.info("✅ 配置验证通过，共 {} 条警告", warnings.size());
        } else {
            log.error("❌ 配置验证失败，发现 {} 个错误:", errors.size());
            for (String error : errors) {
                log.error("  - {}", error);
            }
        }

        if (!warnings.isEmpty()) {
            log.warn("⚠️  发现 {} 条警告:", warnings.size());
            for (String warning : warnings) {
                log.warn("  - {}", warning);
            }
        }

        log.info("========================================");
        log.info("配置验证完成");
        log.info("========================================");

        // 如果有严重错误，抛出异常阻止启动
        if (!errors.isEmpty()) {
            throw new IllegalStateException("配置验证失败: " + String.join("; ", errors));
        }
    }

    /**
     * 验证探针基础配置
     */
    private void validateAgentConfig(List<String> errors, List<String> warnings) {
        log.info("验证探针基础配置...");

        if (agentProperties.getCode() == null || agentProperties.getCode().trim().isEmpty()) {
            errors.add("探针编码(agent.code)未配置");
        } else {
            log.info("  ✓ 探针编码: {}", agentProperties.getCode());
        }

        if (agentProperties.getKey() == null || agentProperties.getKey().trim().isEmpty()) {
            warnings.add("探针密钥(agent.key)未配置，部分功能可能受限");
        } else {
            log.info("  ✓ 探针密钥: ***");
        }

        if (agentProperties.getServer() == null) {
            errors.add("管理服务器配置(agent.server)未配置");
        } else {
            String serverUrl = String.format("http://%s:%d",
                agentProperties.getServer().getHost(),
                agentProperties.getServer().getPort());
            log.info("  ✓ 管理服务器: {}", serverUrl);
        }
    }

    /**
     * 验证MinIO配置
     */
    private void validateMinioConfig(List<String> errors, List<String> warnings) {
        log.info("验证MinIO配置...");

        if (minioConfig == null) {
            warnings.add("MinIO配置未找到，文件上传功能将不可用");
            return;
        }

        if (minioConfig.getEndpoint() == null || minioConfig.getEndpoint().trim().isEmpty()) {
            errors.add("MinIO端点(minio.endpoint)未配置");
        } else {
            log.info("  ✓ MinIO端点: {}", minioConfig.getEndpoint());
        }

        if (minioConfig.getAccessKey() == null || minioConfig.getAccessKey().trim().isEmpty()) {
            errors.add("MinIO访问密钥(minio.access-key)未配置");
        }

        if (minioConfig.getSecretKey() == null || minioConfig.getSecretKey().trim().isEmpty()) {
            errors.add("MinIO秘密密钥(minio.secret-key)未配置");
        }

        // 测试MinIO连接
        if (minioSupport.isAvailable()) {
            log.info("  ✓ MinIO连接正常");
        } else {
            warnings.add("MinIO连接测试失败，文件上传功能可能不可用");
        }
    }

    /**
     * 验证插件配置
     */
    private void validatePluginConfig(List<String> errors, List<String> warnings) {
        log.info("验证插件配置...");

        int pluginCount = pluginLoader.getAllPlugins().size();

        if (pluginCount == 0) {
            errors.add("未加载任何SPI插件，数据库探针功能将不可用");
        } else {
            log.info("  ✓ 已加载 {} 个插件", pluginCount);
            log.info("  ✓ 支持的数据库类型: {}", pluginLoader.getSupportedTypes());
        }

        // 检查SPI插件目录
        String spiDir = "./plugin/spi-plugins/";
        if (!Files.exists(Paths.get(spiDir))) {
            warnings.add("SPI插件目录不存在: " + spiDir);
        }
    }

    /**
     * 验证文件路径配置
     */
    private void validatePathConfig(List<String> errors, List<String> warnings) {
        log.info("验证文件路径配置...");

        // 检查日志目录
        String logDir = "./logs";
        if (!Files.exists(Paths.get(logDir))) {
            try {
                Files.createDirectories(Paths.get(logDir));
                log.info("  ✓ 创建日志目录: {}", logDir);
            } catch (Exception e) {
                warnings.add("无法创建日志目录: " + logDir);
            }
        }

        // 检查临时目录
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir != null) {
            File tmpFile = new File(tmpDir);
            if (!tmpFile.canWrite()) {
                errors.add("临时目录不可写: " + tmpDir);
            } else {
                log.info("  ✓ 临时目录: {}", tmpDir);
            }
        }
    }

    /**
     * 验证系统资源
     */
    private void validateSystemResources(List<String> errors, List<String> warnings) {
        log.info("验证系统资源...");

        Runtime runtime = Runtime.getRuntime();

        // 检查可用处理器
        int processors = runtime.availableProcessors();
        log.info("  ✓ 可用CPU核心: {}", processors);

        if (processors < 2) {
            warnings.add("可用CPU核心较少，可能影响性能");
        }

        // 检查内存
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);

        log.info("  ✓ JVM最大内存: {} MB", maxMemory);
        log.info("  ✓ JVM已分配内存: {} MB", totalMemory);
        log.info("  ✓ JVM空闲内存: {} MB", freeMemory);

        if (maxMemory < 256) {
            errors.add("JVM最大内存过小(<256MB)，建议增加-Xmx参数");
        } else if (maxMemory < 512) {
            warnings.add("JVM最大内存偏小(<512MB)，建议增加-Xmx参数以获得更好性能");
        }

        // 检查磁盘空间
        File root = new File("/");
        long usableSpace = root.getUsableSpace() / (1024 * 1024 * 1024);
        long totalSpace = root.getTotalSpace() / (1024 * 1024 * 1024);

        log.info("  ✓ 磁盘总空间: {} GB", totalSpace);
        log.info("  ✓ 磁盘可用空间: {} GB", usableSpace);

        if (usableSpace < 5) {
            errors.add("磁盘可用空间过小(<5GB)，可能导致程序异常");
        } else if (usableSpace < 10) {
            warnings.add("磁盘可用空间偏小(<10GB)，建议清理磁盘空间");
        }
    }
}
