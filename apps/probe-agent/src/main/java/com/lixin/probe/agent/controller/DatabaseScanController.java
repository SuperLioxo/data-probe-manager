package com.lixin.probe.agent.controller;

import com.lixin.probe.agent.config.DatabaseConfigManager;
import com.lixin.probe.agent.config.DatabaseConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库扫描控制器
 * 用于扫描和发现新数据库
 */
@RestController
@RequestMapping("/api/database")
public class DatabaseScanController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseScanController.class);

    @Autowired
    private DatabaseConfigManager databaseConfigManager;

    /**
     * 扫描新数据库
     *
     * @param databaseType 数据库类型（postgresql, mysql等）
     * @return 扫描结果
     */
    @GetMapping("/scan")
    public Map<String, Object> scanDatabases(@RequestParam String databaseType) {
        log.info("========== 开始扫描数据库: type = {} ==========", databaseType);

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 获取现有配置
            List<DatabaseConnectionConfig> existingDatabases = databaseConfigManager.getDatabases();
            log.info("现有数据库配置数量: {}", existingDatabases.size());

            // 2. 过滤出指定类型的数据库连接
            List<DatabaseConnectionConfig> typeSpecificConnections = existingDatabases.stream()
                .filter(db -> db.getDatabaseType().equalsIgnoreCase(databaseType))
                .collect(Collectors.toList());

            if (typeSpecificConnections.isEmpty()) {
                log.warn("未找到类型为 {} 的数据库连接", databaseType);
                result.put("success", false);
                result.put("message", "未找到该类型的数据库连接");
                result.put("newDatabases", Collections.emptyList());
                return result;
            }

            // 3. 使用第一个连接扫描所有数据库
            DatabaseConnectionConfig baseConfig = typeSpecificConnections.get(0);
            log.info("使用连接扫描: {}@{}:{}/{}",
                baseConfig.getUsername(), baseConfig.getHost(), baseConfig.getPort(), "*");

            Set<String> existingDatabaseNames = existingDatabases.stream()
                .map(DatabaseConnectionConfig::getDatabaseName)
                .collect(Collectors.toSet());

            List<Map<String, Object>> newDatabases = scanAllDatabases(
                baseConfig,
                existingDatabaseNames
            );

            log.info("扫描完成: 发现 {} 个新数据库", newDatabases.size());

            result.put("success", true);
            result.put("message", String.format("扫描完成，发现 %d 个新数据库", newDatabases.size()));
            result.put("newDatabases", newDatabases);
            result.put("totalDatabases", newDatabases.size());

            if (!newDatabases.isEmpty()) {
                log.info("新数据库列表:");
                for (Map<String, Object> db : newDatabases) {
                    log.info("  - {}", db.get("databaseName"));
                }
            }

            log.info("========== 扫描完成 ==========");
            return result;

        } catch (Exception e) {
            log.error("扫描数据库失败", e);
            result.put("success", false);
            result.put("message", "扫描失败: " + e.getMessage());
            result.put("newDatabases", Collections.emptyList());
            return result;
        }
    }

    /**
     * 扫描所有数据库
     */
    private List<Map<String, Object>> scanAllDatabases(
            DatabaseConnectionConfig baseConfig,
            Set<String> existingDatabaseNames) {

        List<Map<String, Object>> newDatabases = new ArrayList<>();
        List<String> allDatabases = listAllDatabases(baseConfig);

        for (String dbName : allDatabases) {
            // 跳过已配置的数据库
            if (existingDatabaseNames.contains(dbName)) {
                continue;
            }

            // 跳过系统数据库
            if (isSystemDatabase(dbName, baseConfig.getDatabaseType())) {
                continue;
            }

            // 测试连接
            if (testDatabaseConnection(baseConfig, dbName)) {
                Map<String, Object> dbInfo = new HashMap<>();
                dbInfo.put("databaseName", dbName);
                dbInfo.put("databaseType", baseConfig.getDatabaseType());
                dbInfo.put("host", baseConfig.getHost());
                dbInfo.put("port", baseConfig.getPort());
                dbInfo.put("username", baseConfig.getUsername());
                dbInfo.put("description", "自动发现: " + dbName);

                // 生成统一的probeKey
                String unifiedProbeKey = "AGENT-" + baseConfig.getDatabaseType().toLowerCase();
                dbInfo.put("probeKey", unifiedProbeKey);
                dbInfo.put("instanceId", generateInstanceId(dbName));

                newDatabases.add(dbInfo);
                log.info("✓ 发现新数据库: {}", dbName);
            }
        }

        return newDatabases;
    }

    /**
     * 列出所有数据库
     */
    private List<String> listAllDatabases(DatabaseConnectionConfig config) {
        List<String> databases = new ArrayList<>();
        String dbType = config.getDatabaseType().toLowerCase();

        try {
            // 连接到数据库服务器（不指定具体数据库）
            String url = buildServerUrl(config);
            Connection conn = DriverManager.getConnection(
                url,
                config.getUsername(),
                config.getPassword()
            );

            if ("postgresql".equals(dbType)) {
                // PostgreSQL
                String sql = "SELECT datname FROM pg_database " +
                           "WHERE datistemplate = false " +
                           "AND datname NOT IN ('postgres', 'template0', 'template1')";

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        databases.add(rs.getString("datname"));
                    }
                }
            } else if ("mysql".equals(dbType)) {
                // MySQL
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
                    while (rs.next()) {
                        String dbName = rs.getString("Database");
                        if (!isSystemDatabase(dbName, "mysql")) {
                            databases.add(dbName);
                        }
                    }
                }
            }

            conn.close();
        } catch (Exception e) {
            log.error("列出数据库失败", e);
        }

        return databases;
    }

    /**
     * 测试数据库连接
     */
    private boolean testDatabaseConnection(DatabaseConnectionConfig baseConfig, String dbName) {
        String dbType = baseConfig.getDatabaseType().toLowerCase();
        String url = buildDatabaseUrl(baseConfig, dbName);

        try {
            Connection conn = DriverManager.getConnection(
                url,
                baseConfig.getUsername(),
                baseConfig.getPassword()
            );

            // 执行简单查询测试连接
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    conn.close();
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("数据库 {} 连接失败: {}", dbName, e.getMessage());
            return false;
        }

        return false;
    }

    /**
     * 判断是否为系统数据库
     */
    private boolean isSystemDatabase(String dbName, String dbType) {
        if (dbName == null || dbName.isEmpty()) {
            return true;
        }

        dbType = dbType.toLowerCase();

        if ("postgresql".equals(dbType)) {
            return dbName.startsWith("template_") ||
                   dbName.equals("postgres");
        } else if ("mysql".equals(dbType)) {
            return dbName.equals("information_schema") ||
                   dbName.equals("mysql") ||
                   dbName.equals("performance_schema") ||
                   dbName.equals("sys");
        }

        return false;
    }

    /**
     * 构建服务器URL（不指定数据库）
     */
    private String buildServerUrl(DatabaseConnectionConfig config) {
        String dbType = config.getDatabaseType().toLowerCase();

        if ("postgresql".equals(dbType)) {
            return String.format("jdbc:postgresql://%s:%d/postgres",
                config.getHost(), config.getPort());
        } else if ("mysql".equals(dbType)) {
            return String.format("jdbc:mysql://%s:%d",
                config.getHost(), config.getPort());
        }

        return "";
    }

    /**
     * 构建数据库URL
     */
    private String buildDatabaseUrl(DatabaseConnectionConfig config, String dbName) {
        String dbType = config.getDatabaseType().toLowerCase();

        if ("postgresql".equals(dbType)) {
            return String.format("jdbc:postgresql://%s:%d/%s",
                config.getHost(), config.getPort(), dbName);
        } else if ("mysql".equals(dbType)) {
            return String.format("jdbc:mysql://%s:%d/%s",
                config.getHost(), config.getPort(), dbName);
        }

        return "";
    }

    /**
     * 生成实例ID
     */
    private String generateInstanceId(String dbName) {
        return dbName.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }
}
