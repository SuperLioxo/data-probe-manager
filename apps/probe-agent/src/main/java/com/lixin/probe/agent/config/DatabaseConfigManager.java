package com.lixin.probe.agent.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库配置管理器
 * 负责加载和管理Agent端的数据库连接配置
 *
 * @author Claude Code
 * @since 1.0
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "database")
public class DatabaseConfigManager {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseConfigManager.class);

    private List<DatabaseConnectionConfig> databases = new ArrayList<>();

    /**
     * 加载数据库配置文件
     * 按优先级从多个路径查找：JAR同级目录 → 工作目录 → classpath
     */
    public void loadConfig() {
        // 1. 尝试 JAR 同级目录
        File jarDir = getJarDirectory();
        File configFile = new File(jarDir, "database-config.yml");
        log.info("查找数据库配置: 路径1(JAR同级)={}", configFile.getAbsolutePath());

        // 2. 如果 JAR 同级没有，尝试当前工作目录
        if (!configFile.exists()) {
            configFile = new File("database-config.yml");
            log.info("查找数据库配置: 路径2(工作目录)={}", configFile.getAbsolutePath());
        }

        // 3. 如果文件系统都没有，尝试 classpath
        if (!configFile.exists()) {
            log.info("查找数据库配置: 路径3(classpath)");
            try (InputStream is = getClass().getResourceAsStream("/database-config.yml")) {
                if (is != null) {
                    loadFromInputStream(is, "classpath:/database-config.yml");
                    return;
                }
            } catch (Exception e) {
                log.warn("从 classpath 加载失败", e);
            }
            log.warn("数据库配置文件不存在，使用默认配置");
            createDefaultConfig(configFile);
            return;
        }

        // 从文件系统加载
        loadFromFile(configFile);
    }

    private void loadFromFile(File configFile) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            DatabaseConfigFile config = mapper.readValue(configFile, DatabaseConfigFile.class);
            applyConfig(config, configFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("加载数据库配置失败，文件路径: {}", configFile.getAbsolutePath(), e);
            this.databases = new ArrayList<>();
        }
    }

    private void loadFromInputStream(InputStream is, String source) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            DatabaseConfigFile config = mapper.readValue(is, DatabaseConfigFile.class);
            applyConfig(config, source);
        } catch (Exception e) {
            log.error("从 {} 加载数据库配置失败", source, e);
            this.databases = new ArrayList<>();
        }
    }

    private void applyConfig(DatabaseConfigFile config, String source) {
        if (config != null && config.getDatabases() != null) {
            this.databases = config.getDatabases();
            log.info("成功加载数据库配置({})，共 {} 个实例", source, databases.size());
            for (DatabaseConnectionConfig db : databases) {
                log.info("数据库配置: instanceId={}, type={}, host={}, port={}, databaseName={}, username={}",
                         db.getInstanceId(), db.getDatabaseType(), db.getHost(),
                         db.getPort(), db.getDatabaseName(), db.getUsername());
            }
        }
    }

    private File getJarDirectory() {
        try {
            var location = getClass().getProtectionDomain()
                    .getCodeSource().getLocation();
            if (location == null) {
                return new File(".");
            }
            String path = location.toURI().getPath();
            if (path == null) {
                return new File(".");
            }
            File jarFile = new File(path);
            return jarFile.isDirectory() ? jarFile : jarFile.getParentFile();
        } catch (Exception e) {
            return new File(".");
        }
    }

    /**
     * 初始化时自动加载数据库配置
     */
    @PostConstruct
    public void init() {
        log.info("DatabaseConfigManager初始化，开始加载数据库配置...");
        loadConfig();
    }

    /**
     * 获取启用的数据库配置
     */
    public List<DatabaseConnectionConfig> getEnabledDatabases() {
        if (databases == null) {
            return new ArrayList<>();
        }
        return databases.stream()
                .filter(db -> db.getEnabled() != null && db.getEnabled())
                .toList();
    }

    /**
     * 根据类型获取数据库配置
     */
    public List<DatabaseConnectionConfig> getDatabasesByType(String databaseType) {
        if (databases == null) {
            return new ArrayList<>();
        }
        return databases.stream()
                .filter(db -> db.getDatabaseType().equalsIgnoreCase(databaseType))
                .filter(db -> db.getEnabled() != null && db.getEnabled())
                .toList();
    }

    /**
     * 根据实例ID获取数据库配置
     */
    public DatabaseConnectionConfig getDatabaseById(String instanceId) {
        if (databases == null) {
            return null;
        }
        return databases.stream()
                .filter(db -> db.getInstanceId().equals(instanceId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有数据库配置
     */
    public List<DatabaseConnectionConfig> getAllDatabaseConfigs() {
        if (databases == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(databases);
    }

    /**
     * 创建默认配置文件
     */
    private void createDefaultConfig(File configFile) {
        try {
            // 如果示例文件存在，复制它作为默认配置
            File exampleFile = new File("database-config-example.yml");
            if (exampleFile.exists()) {
                // 可以选择复制示例文件
                log.info("发现配置示例文件: {}", exampleFile.getAbsolutePath());
            }

            // 创建空的配置
            this.databases = new ArrayList<>();
            log.info("创建默认数据库配置: {} 个实例", 0);
        } catch (Exception e) {
            log.error("创建默认配置失败", e);
        }
    }

    /**
     * 数据库配置文件结构
     */
    @Data
    public static class DatabaseConfigFile {
        private List<DatabaseConnectionConfig> databases;
        private GlobalConfig global;
    }

    /**
     * 全局配置
     */
    @Data
    public static class GlobalConfig {
        @JsonProperty("max-connections")
        private Integer maxConnections;
        @JsonProperty("collect-interval")
        private Integer collectInterval;
        @JsonProperty("retry-count")
        private Integer retryCount;
        @JsonProperty("retry-delay")
        private Integer retryDelay;
    }
}
