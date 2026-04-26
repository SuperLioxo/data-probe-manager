package com.lixin.probe.agent.service;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.config.DatabaseConfigManager;
import com.lixin.probe.agent.config.DatabaseConnectionConfig;
import com.lixin.probe.agent.plugin.api.DatabasePlugin;
import com.lixin.probe.agent.plugin.loader.SpiPluginLoader;
import com.lixin.probe.agent.pojo.request.ProbeRequest;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.lixin.probe.agent.result.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 数据库元数据采集服务
 * 使用 SPI 插件加载器管理数据库插件，提供元数据和数据量采集功能
 */
@Service
public class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    @Autowired
    private SpiPluginLoader pluginLoader;

    @Autowired
    private AgentProperties agentProperties;

    @Autowired(required = false)
    private DatabaseConfigManager databaseConfigManager;

    /**
     * 测试所有配置的数据库连接
     */
    public boolean testConnections() {
        log.info("开始测试数据库连接...");

        List<?> databases;
        boolean useDatabaseConfigManager = (databaseConfigManager != null);

        if (useDatabaseConfigManager) {
            databases = databaseConfigManager.getEnabledDatabases();
            log.info("使用DatabaseConfigManager获取数据库配置，共 {} 个实例", databases.size());
        } else {
            databases = agentProperties.getModules().getDatabase().getDatabases();
            log.info("使用AgentProperties获取数据库配置");
        }

        if (databases == null || databases.isEmpty()) {
            log.warn("未配置任何数据库");
            return false;
        }

        boolean allSuccess = true;
        for (Object dbConfigObj : databases) {
            try {
                AgentProperties.DatabaseConfig dbConfig = convertToDatabaseConfig(dbConfigObj, useDatabaseConfigManager);
                DatabasePlugin plugin = pluginLoader.getPlugin(dbConfig.getType());
                if (plugin == null) {
                    log.warn("未找到数据库类型 [{}] 的插件", dbConfig.getType());
                    allSuccess = false;
                    continue;
                }

                Map<String, Object> params = buildConnectionParams(dbConfig);
                Connection conn = plugin.getConnection(params);

                if (conn != null && !conn.isClosed()) {
                    log.info("数据库连接测试成功: {}://{}:{}",
                        dbConfig.getType(), dbConfig.getHost(), dbConfig.getPort());
                    conn.close();
                } else {
                    log.warn("数据库连接测试失败: {}://{}:{}",
                        dbConfig.getType(), dbConfig.getHost(), dbConfig.getPort());
                    allSuccess = false;
                }
            } catch (Exception e) {
                log.error("测试数据库连接异常", e);
                allSuccess = false;
            }
        }

        return allSuccess;
    }

    /**
     * 批量采集数据库元数据
     */
    public ProbeResponse.Metadata collectMetadata() {
        log.info("==================== [collectMetadata] 开始采集数据库元数据 ====================");

        List<?> databases;
        boolean useDatabaseConfigManager = (databaseConfigManager != null);

        log.info("步骤1: 检查数据库配置...");
        if (useDatabaseConfigManager) {
            databases = databaseConfigManager.getEnabledDatabases();
            log.info("  使用DatabaseConfigManager获取数据库配置，共 {} 个实例", databases.size());
            for (Object db : databases) {
                log.info("    - 数据库配置: {}", db);
            }
        } else {
            databases = agentProperties.getModules().getDatabase().getDatabases();
            log.info("  使用AgentProperties获取数据库配置");
        }

        if (databases == null || databases.isEmpty()) {
            log.error("✗ 未配置任何数据库，无法采集元数据");
            return ProbeResponse.Metadata.builder().build();
        }
        log.info("✓ 找到 {} 个数据库配置", databases.size());

        Map<String, Connection> connMap = new HashMap<>();
        Map<String, ProbeResponse.Metadata.Database> metadataMap = new LinkedHashMap<>();

        try {
            log.info("步骤2: 连接到所有数据库...");
            int successCount = 0;
            int failCount = 0;

            for (Object dbConfigObj : databases) {
                try {
                    AgentProperties.DatabaseConfig dbConfig = convertToDatabaseConfig(dbConfigObj, useDatabaseConfigManager);
                    String dbName = dbConfig.getName();
                    log.info("  正在连接数据库: type={}, name={}, host={}:{}/{}",
                             dbConfig.getType(), dbName,
                             dbConfig.getHost(), dbConfig.getPort(), dbName);

                    DatabasePlugin plugin = pluginLoader.getPlugin(dbConfig.getType());
                    if (plugin == null) {
                        log.error("  ✗ 未找到数据库类型 [{}] 的插件", dbConfig.getType());
                        failCount++;
                        continue;
                    }

                    Map<String, Object> params = buildConnectionParams(dbConfig);
                    Connection conn = plugin.getConnection(params);

                    String key = dbConfig.getType() + "_" + dbConfig.getName();
                    connMap.put(key, conn);
                    successCount++;
                    log.info("  ✓ 数据库连接成功: {}", key);
                } catch (Exception e) {
                    log.error("  ✗ 获取数据库连接失败", e);
                    failCount++;
                }
            }

            log.info("连接结果: 成功={}, 失败={}", successCount, failCount);

            if (connMap.isEmpty()) {
                log.error("✗ 未获取到任何数据库连接，无法采集元数据");
                return ProbeResponse.Metadata.builder().build();
            }
            log.info("✓ 成功获取 {} 个数据库连接", connMap.size());

            log.info("步骤3: 采集数据库元数据...");
            int metadataSuccessCount = 0;
            int metadataFailCount = 0;

            for (Object dbConfigObj : databases) {
                try {
                    AgentProperties.DatabaseConfig dbConfig = convertToDatabaseConfig(dbConfigObj, useDatabaseConfigManager);
                    String key = dbConfig.getType() + "_" + dbConfig.getName();
                    Connection conn = connMap.get(key);

                    if (conn == null) {
                        log.warn("  跳过（无连接）: {}", key);
                        continue;
                    }

                    log.info("  正在采集元数据: {} ({})", key, dbConfig.getName());

                    DatabasePlugin plugin = pluginLoader.getPlugin(dbConfig.getType());
                    ProbeRequest request = buildProbeRequest(dbConfig);

                    log.info("  调用 plugin.getMetadata(conn, request)");
                    log.info("  ProbeRequest: {}", request);
                    log.info("  request.getDatabase(): {}", request.getDatabase());

                    CompletableFuture<ProbeResponse.Metadata> future = plugin.getMetadata(conn, request);
                    ProbeResponse.Metadata metadata = future.get();

                    if (metadata != null && metadata.getDatabases() != null) {
                        metadataMap.putAll(metadata.getDatabases());
                        int tableCount = metadata.getDatabases().values().stream()
                                .mapToInt(db -> {
                                    Map<String, ProbeResponse.Metadata.Table> tables = db.getTables();
                                    return tables != null ? tables.size() : 0;
                                })
                                .sum();
                        log.info("  ✓ 采集成功: {}, 表数={}", key, tableCount);
                        metadataSuccessCount++;
                    } else {
                        log.warn("  ⚠️  采集结果为空: {}", key);
                        metadataFailCount++;
                    }
                } catch (Exception e) {
                    log.error("  ✗ 采集数据库元数据失败", e);
                    metadataFailCount++;
                }
            }

            log.info("元数据采集结果: 成功={}, 失败={}", metadataSuccessCount, metadataFailCount);
            log.info("✓ 数据库元数据采集完成，共采集 {} 个数据库的元数据", metadataMap.size());
            log.info("=========================================================================");

            return ProbeResponse.Metadata.builder()
                    .type("database")
                    .databases(metadataMap)
                    .build();

        } catch (Exception e) {
            log.error("✗ 采集数据库元数据异常", e);
            log.info("=========================================================================");
            return ProbeResponse.Metadata.builder().build();
        } finally {
            log.info("步骤4: 关闭所有数据库连接...");
            int closedCount = 0;
            for (Connection conn : connMap.values()) {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                        closedCount++;
                    }
                } catch (Exception e) {
                    log.error("  ✗ 关闭数据库连接失败", e);
                }
            }
            log.info("✓ 关闭了 {} 个数据库连接", closedCount);
        }
    }

    /**
     * 转换为AgentProperties.DatabaseConfig
     * ⭐ 使用统一probeKey：所有同类型数据库共享同一个probeKey
     */
    private AgentProperties.DatabaseConfig convertToDatabaseConfig(Object dbConfigObj, boolean useDatabaseConfigManager) {
        if (dbConfigObj instanceof AgentProperties.DatabaseConfig) {
            return (AgentProperties.DatabaseConfig) dbConfigObj;
        }

        if (useDatabaseConfigManager && databaseConfigManager != null) {
            DatabaseConnectionConfig connConfig = (DatabaseConnectionConfig) dbConfigObj;

            AgentProperties.DatabaseConfig dbConfig = new AgentProperties.DatabaseConfig();
            dbConfig.setType(connConfig.getDatabaseType());
            dbConfig.setHost(connConfig.getHost());
            dbConfig.setPort(connConfig.getPort());
            dbConfig.setName(connConfig.getDatabaseName());
            dbConfig.setUsername(connConfig.getUsername());
            dbConfig.setPassword(connConfig.getPassword());
            dbConfig.setSchemas(connConfig.getSchemas());
            dbConfig.setConnectTimeout(connConfig.getConnectionTimeout());
            dbConfig.setQueryTimeout(connConfig.getQueryTimeout());

            // ⭐ 关键修改：使用统一的probeKey（按数据库类型）
            // 所有PostgreSQL数据库使用 AGENT-postgresql
            // 所有MySQL数据库使用 AGENT-mysql
            // 所有Oracle数据库使用 AGENT-oracle
            String unifiedProbeKey = "AGENT-" + connConfig.getDatabaseType().toLowerCase();
            dbConfig.setProbeKey(unifiedProbeKey);

            log.info("使用统一probeKey: databaseType={}, probeKey={}, databaseName={}",
                     connConfig.getDatabaseType(), unifiedProbeKey, connConfig.getDatabaseName());

            return dbConfig;
        }

        throw new IllegalArgumentException("无法识别的数据库配置类型: " + dbConfigObj.getClass());
    }

    // Keep other existing methods that were working before
    public ProbeResponse.DataSize collectDataSize() {
        log.info("==================== [collectDataSize] 开始采集数据库数据量信息 ====================");

        List<?> databases;
        boolean useDatabaseConfigManager = (databaseConfigManager != null);

        log.info("步骤1: 检查数据库配置...");
        if (useDatabaseConfigManager) {
            databases = databaseConfigManager.getEnabledDatabases();
            log.info("  使用DatabaseConfigManager获取数据库配置，共 {} 个实例", databases.size());
        } else {
            databases = agentProperties.getModules().getDatabase().getDatabases();
            log.info("  使用AgentProperties获取数据库配置");
        }

        if (databases == null || databases.isEmpty()) {
            log.error("✗ 未配置任何数据库，无法采集数据量信息");
            return ProbeResponse.DataSize.builder().build();
        }
        log.info("✓ 找到 {} 个数据库配置", databases.size());

        Map<String, Connection> connMap = new HashMap<>();
        Map<String, ProbeResponse.DataSize.Database> dataSizeMap = new LinkedHashMap<>();

        try {
            log.info("步骤2: 连接到所有数据库...");
            int successCount = 0;
            int failCount = 0;

            for (Object dbConfigObj : databases) {
                try {
                    AgentProperties.DatabaseConfig dbConfig = convertToDatabaseConfig(dbConfigObj, useDatabaseConfigManager);
                    String dbName = dbConfig.getName();
                    log.info("  正在连接数据库: type={}, name={}, host={}:{}/{}",
                             dbConfig.getType(), dbName,
                             dbConfig.getHost(), dbConfig.getPort(), dbName);

                    DatabasePlugin plugin = pluginLoader.getPlugin(dbConfig.getType());
                    if (plugin == null) {
                        log.error("  ✗ 未找到数据库类型 [{}] 的插件", dbConfig.getType());
                        failCount++;
                        continue;
                    }

                    Map<String, Object> params = buildConnectionParams(dbConfig);
                    Connection conn = plugin.getConnection(params);

                    String key = dbConfig.getType() + "_" + dbConfig.getName();
                    connMap.put(key, conn);
                    successCount++;
                    log.info("  ✓ 数据库连接成功: {}", key);
                } catch (Exception e) {
                    log.error("  ✗ 获取数据库连接失败", e);
                    failCount++;
                }
            }

            log.info("连接结果: 成功={}, 失败={}", successCount, failCount);

            if (connMap.isEmpty()) {
                log.error("✗ 未获取到任何数据库连接，无法采集数据量信息");
                return ProbeResponse.DataSize.builder().build();
            }
            log.info("✓ 成功获取 {} 个数据库连接", connMap.size());

            log.info("步骤3: 采集数据库数据量信息...");
            int dataSizeSuccessCount = 0;
            int dataSizeFailCount = 0;

            for (Object dbConfigObj : databases) {
                try {
                    AgentProperties.DatabaseConfig dbConfig = convertToDatabaseConfig(dbConfigObj, useDatabaseConfigManager);
                    String key = dbConfig.getType() + "_" + dbConfig.getName();
                    Connection conn = connMap.get(key);

                    if (conn == null) {
                        log.warn("  跳过（无连接）: {}", key);
                        continue;
                    }

                    log.info("  正在采集数据量信息: {} ({})", key, dbConfig.getName());

                    DatabasePlugin plugin = pluginLoader.getPlugin(dbConfig.getType());

                    // 调用插件的数据量采集方法
                    CompletableFuture<ProbeResponse.DataSize> future = plugin.getDataSize(conn, buildProbeRequest(dbConfig));
                    ProbeResponse.DataSize dataSize = future.get();

                    if (dataSize != null && dataSize.getDatabases() != null) {
                        dataSizeMap.putAll(dataSize.getDatabases());
                        int tableCount = dataSize.getDatabases().values().stream()
                                .mapToInt(db -> {
                                    Map<String, ProbeResponse.DataSize.Table> tables = db.getTables();
                                    return tables != null ? tables.size() : 0;
                                })
                                .sum();
                        log.info("  ✓ 采集成功: {}, 表数={}", key, tableCount);
                        dataSizeSuccessCount++;
                    } else {
                        log.warn("  ⚠️  采集结果为空: {}", key);
                        dataSizeFailCount++;
                    }
                } catch (Exception e) {
                    log.error("  ✗ 采集失败", e);
                    dataSizeFailCount++;
                }
            }

            log.info("数据量采集结果: 成功={}, 失败={}", dataSizeSuccessCount, dataSizeFailCount);

            log.info("步骤4: 关闭所有数据库连接...");
            int closedCount = 0;
            for (Map.Entry<String, Connection> entry : connMap.entrySet()) {
                try {
                    entry.getValue().close();
                    closedCount++;
                } catch (Exception e) {
                    log.error("关闭数据库连接失败: {}", entry.getKey(), e);
                }
            }
            log.info("✓ 关闭了 {} 个数据库连接", closedCount);

            ProbeResponse.DataSize result = ProbeResponse.DataSize.builder()
                    .databases(dataSizeMap)
                    .build();

            log.info("✓ 数据库数据量采集完成，共采集 {} 个数据库的数据量信息", dataSizeMap.size());
            log.info("=======================================================================");
            return result;

        } catch (Exception e) {
            log.error("✗ 采集数据库数据量失败", e);

            // 确保关闭所有连接
            for (Map.Entry<String, Connection> entry : connMap.entrySet()) {
                try {
                    if (entry.getValue() != null && !entry.getValue().isClosed()) {
                        entry.getValue().close();
                    }
                } catch (Exception ex) {
                    log.error("关闭数据库连接失败: {}", entry.getKey(), ex);
                }
            }

            log.info("=======================================================================");
            return ProbeResponse.DataSize.builder().build();
        }
    }

    public CompletableFuture<ProbeResponse.DataSize> collectDataSizeAsync() {
        return CompletableFuture.supplyAsync(this::collectDataSize);
    }

    public CompletableFuture<ProbeResponse.Metadata> collectMetadataAsync() {
        return CompletableFuture.supplyAsync(this::collectMetadata);
    }

    public boolean addOrUpdateDatabaseConfig(String probeKey, AgentProperties.DatabaseConfig config) {
        log.info("添加或更新数据库配置: probeKey={}, type={}, name={}",
                 probeKey, config.getType(), config.getName());

        try {
            // If DatabaseConfigManager is available, update it
            if (databaseConfigManager != null) {
                // Convert to DatabaseConnectionConfig
                List<DatabaseConnectionConfig> databases = databaseConfigManager.getDatabases();

                // Check if database with this probeKey already exists
                DatabaseConnectionConfig existingConfig = databases.stream()
                    .filter(db -> db.getInstanceId().equals(probeKey))
                    .findFirst()
                    .orElse(null);

                if (existingConfig != null) {
                    // Update existing configuration
                    existingConfig.setDatabaseType(config.getType());
                    existingConfig.setHost(config.getHost());
                    existingConfig.setPort(config.getPort());
                    existingConfig.setDatabaseName(config.getName());
                    existingConfig.setUsername(config.getUsername());
                    existingConfig.setPassword(config.getPassword());
                    existingConfig.setSchemas(config.getSchemas());
                    existingConfig.setConnectionTimeout(config.getConnectTimeout());
                    existingConfig.setQueryTimeout(config.getQueryTimeout());
                    existingConfig.setProbeKey(probeKey);  // ⭐ 设置probeKey

                    log.info("更新现有数据库配置: probeKey={}", probeKey);
                } else {
                    // Add new configuration
                    DatabaseConnectionConfig newConfig = new DatabaseConnectionConfig();
                    newConfig.setInstanceId(probeKey);
                    newConfig.setDatabaseType(config.getType());
                    newConfig.setHost(config.getHost());
                    newConfig.setPort(config.getPort());
                    newConfig.setDatabaseName(config.getName());
                    newConfig.setUsername(config.getUsername());
                    newConfig.setPassword(config.getPassword());
                    newConfig.setSchemas(config.getSchemas());
                    newConfig.setConnectionTimeout(config.getConnectTimeout());
                    newConfig.setQueryTimeout(config.getQueryTimeout());
                    newConfig.setEnabled(true);
                    newConfig.setProbeKey(probeKey);  // ⭐ 设置probeKey

                    databases.add(newConfig);
                    log.info("添加新数据库配置: probeKey={}", probeKey);
                }

                return true;
            }

            // Fallback to AgentProperties if DatabaseConfigManager is not available
            List<AgentProperties.DatabaseConfig> databases =
                agentProperties.getModules().getDatabase().getDatabases();

            if (databases == null) {
                return false;
            }

            // Check if database with this probeKey already exists
            AgentProperties.DatabaseConfig existingConfig = databases.stream()
                .filter(db -> probeKey.equals(db.getProbeKey()))
                .findFirst()
                .orElse(null);

            if (existingConfig != null) {
                // Update existing configuration
                existingConfig.setType(config.getType());
                existingConfig.setHost(config.getHost());
                existingConfig.setPort(config.getPort());
                existingConfig.setName(config.getName());
                existingConfig.setUsername(config.getUsername());
                existingConfig.setPassword(config.getPassword());
                existingConfig.setSchemas(config.getSchemas());
                existingConfig.setConnectTimeout(config.getConnectTimeout());
                existingConfig.setQueryTimeout(config.getQueryTimeout());

                log.info("更新现有数据库配置: probeKey={}", probeKey);
            } else {
                // Add new configuration
                databases.add(config);
                log.info("添加新数据库配置: probeKey={}", probeKey);
            }

            return true;

        } catch (Exception e) {
            log.error("添加或更新数据库配置失败: probeKey={}", probeKey, e);
            return false;
        }
    }

    public AgentProperties.DatabaseConfig getDatabaseConfig(String probeKey) {
        if (databaseConfigManager != null) {
            DatabaseConnectionConfig config = databaseConfigManager.getDatabaseById(probeKey);
            if (config != null) {
                return convertToDatabaseConfig(config, true);
            }
        }

        List<AgentProperties.DatabaseConfig> databases =
            agentProperties.getModules().getDatabase().getDatabases();

        if (databases == null) {
            return null;
        }

        return databases.stream()
            .filter(config -> probeKey.equals(config.getName()) ||
                             probeKey.equals(config.getProbeKey()))
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> buildConnectionParams(AgentProperties.DatabaseConfig dbConfig) {
        Map<String, Object> params = new HashMap<>();
        params.put("host", dbConfig.getHost());
        params.put("port", dbConfig.getPort());
        params.put("databaseName", dbConfig.getName());
        params.put("username", dbConfig.getUsername());
        params.put("password", dbConfig.getPassword());
        params.put("schemas", dbConfig.getSchemas());
        params.put("connectTimeout", dbConfig.getConnectTimeout());
        params.put("queryTimeout", dbConfig.getQueryTimeout());
        return params;
    }

    private ProbeRequest buildProbeRequest(AgentProperties.DatabaseConfig dbConfig) {
        ProbeRequest.DatabaseConfig requestDbConfig = ProbeRequest.DatabaseConfig.builder()
                .probeKey(dbConfig.getProbeKey())
                .type(dbConfig.getType())
                .host(dbConfig.getHost())
                .port(dbConfig.getPort())
                .name(dbConfig.getName())
                .schemas(dbConfig.getSchemas())
                .build();

        return ProbeRequest.builder()
                .database(requestDbConfig)
                .build();
    }

    public boolean isDatabaseConfigured() {
        if (databaseConfigManager != null) {
            return !databaseConfigManager.getEnabledDatabases().isEmpty();
        }
        List<AgentProperties.DatabaseConfig> databases = agentProperties.getModules().getDatabase().getDatabases();
        return databases != null && !databases.isEmpty();
    }
}