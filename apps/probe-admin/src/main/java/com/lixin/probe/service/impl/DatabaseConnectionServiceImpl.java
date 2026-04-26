package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.entity.DatabaseConnection;
import com.lixin.probe.exception.BusinessException;
import com.lixin.probe.mapper.DatabaseConnectionMapper;
import com.lixin.probe.service.DatabaseConnectionService;
import com.lixin.probe.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据库连接Service实现
 *
 * @author Claude Code
 * @date 2026-03-26
 */
@Slf4j
@Service
public class DatabaseConnectionServiceImpl implements DatabaseConnectionService {

    @Autowired
    private DatabaseConnectionMapper databaseConnectionMapper;

    @Value("${probe.encryption.key:dpm-probe-encryption-key-2024}")
    private String encryptionKey;

    @Override
    public List<DatabaseConnection> getAllConnections() {
        return databaseConnectionMapper.selectList(null);
    }

    @Override
    public List<DatabaseConnection> getConnectionsByType(String databaseType) {
        log.info("[DatabaseConnectionService] 查询数据库类型: {}", databaseType);
        return databaseConnectionMapper.findByDatabaseType(databaseType);
    }

    @Override
    public DatabaseConnection getConnectionById(Long id) {
        return databaseConnectionMapper.findById(id);
    }

    @Override
    public DatabaseConnection createConnection(DatabaseConnection connection) {
        log.info("[DatabaseConnectionService] 创建数据库连接: {}", connection.getName());

        // 测试连接
        if (!testConnection(connection)) {
            throw new BusinessException("数据库连接测试失败，请检查配置");
        }

        // 加密密码
        encryptPassword(connection);

        connection.setCreatedAt(LocalDateTime.now());
        connection.setUpdatedAt(LocalDateTime.now());

        databaseConnectionMapper.insert(connection);
        log.info("[DatabaseConnectionService] 数据库连接创建成功: id={}", connection.getId());

        return connection;
    }

    @Override
    public void updateConnection(DatabaseConnection connection) {
        log.info("[DatabaseConnectionService] 更新数据库连接: id={}", connection.getId());

        // 如果密码被修改（不是已加密格式），需要重新加密
        if (connection.getPassword() != null && !connection.getPassword().contains(":")) {
            encryptPassword(connection);
        }

        connection.setUpdatedAt(LocalDateTime.now());
        databaseConnectionMapper.updateById(connection);

        log.info("[DatabaseConnectionService] 数据库连接更新成功");
    }

    @Override
    public void deleteConnection(Long id) {
        log.info("[DatabaseConnectionService] 删除数据库连接: id={}", id);
        databaseConnectionMapper.deleteById(id);
        log.info("[DatabaseConnectionService] 数据库连接删除成功");
    }

    @Override
    public boolean testConnection(DatabaseConnection connection) {
        log.info("[DatabaseConnectionService] 测试数据库连接: type={}, host:{}, port:{}, database={}",
                connection.getDatabaseType(), connection.getDatabaseHost(),
                connection.getDatabasePort(), connection.getDatabaseName());

        Connection conn = null;
        try {
            String jdbcUrl = buildJdbcUrl(connection);
            log.info("[DatabaseConnectionService] JDBC URL: {}", jdbcUrl);

            conn = DriverManager.getConnection(
                    jdbcUrl,
                    connection.getUsername(),
                    decryptPassword(connection.getPassword())
            );

            boolean isValid = conn.isValid(5);
            log.info("[DatabaseConnectionService] 连接测试结果: {}", isValid ? "成功" : "失败");

            return isValid;
        } catch (Exception e) {
            log.error("[DatabaseConnectionService] 数据库连接测试失败", e);
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception e) {
                    log.error("[DatabaseConnectionService] 关闭连接失败", e);
                }
            }
        }
    }

    /**
     * 加密密码
     */
    private void encryptPassword(DatabaseConnection connection) {
        String password = connection.getPassword();
        try {
            connection.setPassword(CryptoUtil.encryptAES256(password, encryptionKey));
        } catch (Exception e) {
            log.warn("[DatabaseConnectionService] 密码加密失败, 使用原始存储: {}", e.getMessage());
        }
    }

    private String decryptPassword(String encrypted) {
        if (encrypted == null) return null;
        try {
            return CryptoUtil.decryptAES256(encrypted, encryptionKey);
        } catch (Exception e) {
            log.warn("[DatabaseConnectionService] 密码解密失败, 返回原文");
            return encrypted;
        }
    }

    /**
     * 构建JDBC URL
     */
    private String buildJdbcUrl(DatabaseConnection connection) {
        return switch (connection.getDatabaseType()) {
            case "MySQL" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                    connection.getDatabaseHost(), connection.getDatabasePort(), connection.getDatabaseName());
            case "PostgreSQL" -> String.format("jdbc:postgresql://%s:%d/%s",
                    connection.getDatabaseHost(), connection.getDatabasePort(), connection.getDatabaseName());
            case "Oracle" -> String.format("jdbc:oracle:thin:@%s:%d:%s",
                    connection.getDatabaseHost(), connection.getDatabasePort(), connection.getDatabaseName());
            case "SQL Server" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s",
                    connection.getDatabaseHost(), connection.getDatabasePort(), connection.getDatabaseName());
            default -> throw new IllegalArgumentException("不支持的数据库类型: " + connection.getDatabaseType());
        };
    }
}
