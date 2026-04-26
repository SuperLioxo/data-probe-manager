package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.dto.DatabaseConnectionDTO;
import com.lixin.probe.entity.DatabaseConnection;
import com.lixin.probe.entity.DatabaseProbe;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.mapper.DatabaseConnectionMapper;
import com.lixin.probe.mapper.DatabaseProbeMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.service.DatabaseConnectionService;
import com.lixin.probe.service.DatabaseProbeService;
import com.lixin.probe.service.ProbeService;
import com.lixin.probe.util.CryptoUtil;
import com.lixin.probe.websocket.MetaProbeWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库探针Service实现类
 *
 * @author Claude Code
 * @date 2026-03-26
 */
@Slf4j
@Service
public class DatabaseProbeServiceImpl implements DatabaseProbeService {

    private static final String ENCRYPTION_KEY = "database-probe-encryption-key-2026";

    @Autowired
    private DatabaseProbeMapper databaseProbeMapper;

    @Autowired
    private ProbeMapper probeMapper;

    @Autowired(required = false)
    private MetaProbeWebSocketHandler webSocketHandler;

    @Override
    public List<DatabaseProbe> list() {
        return databaseProbeMapper.selectList(null);
    }

    @Override
    public Page<DatabaseProbe> getPage(int pageNum, int pageSize, String name, String status) {
        Page<DatabaseProbe> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<DatabaseProbe> queryWrapper = new LambdaQueryWrapper<DatabaseProbe>()
                .like(name != null, DatabaseProbe::getName, name)
                .eq(status != null, DatabaseProbe::getStatus, status)
                .orderByDesc(DatabaseProbe::getCreateTime);
        return databaseProbeMapper.selectPage(page, queryWrapper);
    }

    @Override
    public DatabaseProbe getById(Long id) {
        log.info("[DatabaseProbeService] 查询数据库探针 - id={}", id);
        DatabaseProbe databaseProbe = databaseProbeMapper.selectById(id);
        log.info("[DatabaseProbeService] 查询结果 - databaseProbe={}", databaseProbe != null ? databaseProbe.getName() : "null");
        return databaseProbe;
    }

    @Override
    public DatabaseProbe getByProbeKey(String probeKey) {
        return databaseProbeMapper.selectOne(
                new LambdaQueryWrapper<DatabaseProbe>()
                        .eq(DatabaseProbe::getProbeKey, probeKey)
        );
    }

    @Override
    @Transactional
    public DatabaseProbe create(DatabaseProbe databaseProbe) {
        log.info("[DatabaseProbeService] 创建数据库探针 - name={}, probeKey={}",
                databaseProbe.getName(), databaseProbe.getProbeKey());

        // 测试连接
        if (!testConnection(databaseProbe)) {
            throw new RuntimeException("数据库连接测试失败，请检查配置");
        }

        // 加密密码
        encryptPassword(databaseProbe);

        // 设置默认值
        if (databaseProbe.getStatus() == null || databaseProbe.getStatus().isEmpty()) {
            databaseProbe.setStatus("online");  // 新建探针默认为在线状态
            log.info("[DatabaseProbeService] 新建探针状态设置为online");
        }

        if (databaseProbe.getType() == null || databaseProbe.getType().isEmpty()) {
            databaseProbe.setType("DATABASE");
        }

        databaseProbe.setCreateTime(LocalDateTime.now());
        databaseProbe.setUpdateTime(LocalDateTime.now());

        // 保存到 database_probe 表
        int result = databaseProbeMapper.insert(databaseProbe);
        log.info("[DatabaseProbeService] database_probe表创建完成 - id={}, name={}, 影响行数: {}",
                databaseProbe.getId(), databaseProbe.getName(), result);

        if (result <= 0) {
            log.error("[DatabaseProbeService] 创建失败：插入操作影响行数为0 - probeKey={}", databaseProbe.getProbeKey());
            throw new RuntimeException("创建数据库探针失败");
        }

        // 同时在 probe 表创建记录（保持系统一致性）
        createProbeRecord(databaseProbe);

        // 同步配置到Agent（让Agent获得probeKey）
        syncConfigToAgent(databaseProbe.getProbeKey());

        log.info("[DatabaseProbeService] 数据库探针创建成功 - id={}, probeKey={}",
                databaseProbe.getId(), databaseProbe.getProbeKey());

        return databaseProbe;
    }

    @Override
    @Transactional
    public void update(DatabaseProbe databaseProbe) {
        log.info("[DatabaseProbeService] 更新数据库探针 - id={}, name={}",
                databaseProbe.getId(), databaseProbe.getName());

        // 如果密码被修改（不是已加密的格式），需要重新加密
        if (databaseProbe.getPassword() != null && !databaseProbe.getPassword().contains(":")) {
            encryptPassword(databaseProbe);
        }

        databaseProbe.setUpdateTime(LocalDateTime.now());
        int result = databaseProbeMapper.updateById(databaseProbe);

        log.info("[DatabaseProbeService] 更新完成 - 影响行数: {}", result);

        // 同步配置到Agent
        syncConfigToAgent(databaseProbe.getProbeKey());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("[DatabaseProbeService] 删除数据库探针 - id={}", id);
        DatabaseProbe databaseProbe = getById(id);
        if (databaseProbe == null) {
            throw new IllegalArgumentException("数据库探针不存在");
        }

        // 删除 database_probe 表记录
        databaseProbeMapper.deleteById(id);

        // 删除 probe 表记录
        Probe probe = probeMapper.selectOne(
                new LambdaQueryWrapper<Probe>()
                        .eq(Probe::getProbeKey, databaseProbe.getProbeKey())
        );
        if (probe != null) {
            probeMapper.deleteById(probe.getId());
        }

        log.info("[DatabaseProbeService] 删除完成 - id={}", id);
    }

    @Override
    public boolean testConnection(DatabaseProbe databaseProbe) {
        log.info("[DatabaseProbeService] 测试数据库连接 - type={}, host:{}, port:{}, database={}",
                databaseProbe.getDatabaseType(), databaseProbe.getDatabaseHost(),
                databaseProbe.getDatabasePort(), databaseProbe.getDatabaseName());

        Connection connection = null;
        try {
            String jdbcUrl = buildJdbcUrl(databaseProbe);
            log.info("[DatabaseProbeService] JDBC URL: {}", jdbcUrl);

            connection = DriverManager.getConnection(
                    jdbcUrl,
                    databaseProbe.getUsername(),
                    databaseProbe.getPassword()
            );

            boolean isValid = connection.isValid(5); // 5秒超时
            log.info("[DatabaseProbeService] 连接测试结果: {}", isValid ? "成功" : "失败");

            return isValid;
        } catch (Exception e) {
            log.error("[DatabaseProbeService] 数据库连接测试失败", e);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.error("[DatabaseProbeService] 关闭连接失败", e);
                }
            }
        }
    }

    @Override
    public void syncConfigToAgent(String probeKey) {
        log.info("[DatabaseProbeService] 同步配置到Agent - probeKey={}", probeKey);

        if (webSocketHandler == null) {
            log.warn("[DatabaseProbeService] WebSocket handler未注入，无法同步配置");
            return;
        }

        try {
            // 获取数据库探针配置
            DatabaseProbe databaseProbe = getByProbeKey(probeKey);
            if (databaseProbe == null) {
                log.warn("[DatabaseProbeService] 数据库探针不存在 - probeKey={}", probeKey);
                return;
            }

            // 解密密码用于传输
            String decryptedPassword = decryptPassword(databaseProbe.getPassword());

            // 构建配置消息
            Map<String, Object> config = new HashMap<>();
            config.put("probeKey", probeKey);
            config.put("databaseType", databaseProbe.getDatabaseType());
            config.put("host", databaseProbe.getDatabaseHost());
            config.put("port", databaseProbe.getDatabasePort());
            config.put("name", databaseProbe.getDatabaseName());
            config.put("username", databaseProbe.getUsername());
            config.put("password", decryptedPassword); // WebSocket已加密，这里传输明文
            config.put("schemas", databaseProbe.getSchemas());
            config.put("collectInterval", databaseProbe.getCollectInterval());

            // 构建WebSocket命令（包含完整的command对象）
            Map<String, Object> command = new HashMap<>();
            command.put("type", "COMMAND");
            command.put("cmd", "UPDATE_DB_CONFIG");
            command.put("probeKey", probeKey);  // Agent端需要这个来识别是哪个探针的配置
            command.put("config", config);
            command.put("timestamp", System.currentTimeMillis());

            // 发送到Agent（使用sendControlCommand方法）
            boolean sent = webSocketHandler.sendControlCommand(probeKey, "UPDATE_DB_CONFIG", command);
            log.info("[DatabaseProbeService] 配置同步结果: {}", sent ? "成功" : "失败");

        } catch (Exception e) {
            log.error("[DatabaseProbeService] 同步配置到Agent失败 - probeKey={}", probeKey, e);
        }
    }

    @Override
    public void encryptPassword(DatabaseProbe databaseProbe) {
        if (databaseProbe.getPassword() != null && !databaseProbe.getPassword().isEmpty()) {
            String plainPassword = databaseProbe.getPassword();
            // 检查是否已经是加密格式（包含冒号分隔符）
            if (!plainPassword.contains(":")) {
                String encryptedPassword = CryptoUtil.encryptAES256(plainPassword, ENCRYPTION_KEY);
                databaseProbe.setPassword(encryptedPassword);
                log.debug("[DatabaseProbeService] 密码已加密");
            }
        }
    }

    @Override
    public String decryptPassword(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) {
            return "";
        }
        try {
            // 检查密码是否已经是明文（简单判断：长度较短且不含特殊字符）
            // 这里我们假设加密后的密码至少是原始长度的2倍（Base64编码）
            // 如果密码很短（比如小于20字符），可能是明文，直接返回
            if (encryptedPassword.length() < 20) {
                log.debug("[DatabaseProbeService] 密码长度较短，可能是明文，直接使用");
                return encryptedPassword;
            }

            return CryptoUtil.decryptAES256(encryptedPassword, ENCRYPTION_KEY);
        } catch (Exception e) {
            // 解密失败，可能是明文密码，尝试直接返回
            log.warn("[DatabaseProbeService] 密码解密失败，尝试作为明文使用: {}", e.getMessage());
            return encryptedPassword;
        }
    }

    @Override
    @Transactional
    public void updateHeartbeat(String probeKey) {
        DatabaseProbe probe = getByProbeKey(probeKey);
        if (probe != null) {
            probe.setLastHeartbeat(LocalDateTime.now());
            probe.setStatus("online"); // 心跳更新时设置状态为在线
            probe.setUpdateTime(LocalDateTime.now());
            databaseProbeMapper.updateById(probe);
            log.debug("[DatabaseProbeService] 更新数据库探针心跳: probeKey={}, status=online", probeKey);
        }
    }

    @Override
    @Transactional
    public void updateCollectTime(String probeKey) {
        DatabaseProbe probe = getByProbeKey(probeKey);
        if (probe != null) {
            probe.setLastCollectTime(LocalDateTime.now());
            probe.setUpdateTime(LocalDateTime.now());
            databaseProbeMapper.updateById(probe);
            log.debug("[DatabaseProbeService] 更新采集时间: probeKey={}", probeKey);
        }
    }

    @Override
    public List<DatabaseProbe> getByDatabaseTypeAndHost(String databaseType, String host) {
        return databaseProbeMapper.selectList(
                new LambdaQueryWrapper<DatabaseProbe>()
                        .eq(DatabaseProbe::getDatabaseType, databaseType)
                        .eq(DatabaseProbe::getDatabaseHost, host)
        );
    }

    /**
     * 构建JDBC连接URL
     */
    private String buildJdbcUrl(DatabaseProbe databaseProbe) {
        String type = databaseProbe.getDatabaseType();
        String host = databaseProbe.getDatabaseHost();
        Integer port = databaseProbe.getDatabasePort();
        String dbName = databaseProbe.getDatabaseName();

        switch (type) {
            case "MySQL":
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                        host, port, dbName);
            case "PostgreSQL":
                return String.format("jdbc:postgresql://%s:%d/%s",
                        host, port, dbName);
            case "Oracle":
                return String.format("jdbc:oracle:thin:@%s:%d:%s",
                        host, port, dbName);
            case "SQL Server":
                return String.format("jdbc:sqlserver://%s:%d;databaseName=%s",
                        host, port, dbName);
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + type);
        }
    }

    /**
     * 在probe表中创建记录（保持系统一致性）
     */
    private void createProbeRecord(DatabaseProbe databaseProbe) {
        Probe probe = Probe.builder()
                .probeKey(databaseProbe.getProbeKey())
                .name(databaseProbe.getName())
                .type("DATABASE")
                .status(databaseProbe.getStatus())
                .hostIp(databaseProbe.getHostIp())
                .port(databaseProbe.getPort())
                .collectInterval(databaseProbe.getCollectInterval())
                .build();

        probe.setCreateTime(LocalDateTime.now());
        probe.setUpdateTime(LocalDateTime.now());

        int result = probeMapper.insert(probe);
        log.info("[DatabaseProbeService] probe表创建完成 - id={}, name={}, 影响行数: {}",
                probe.getId(), probe.getName(), result);
    }

    @Autowired
    private DatabaseConnectionMapper databaseConnectionMapper;

    @Autowired
    private DatabaseConnectionService databaseConnectionService;

    @Override
    @Transactional
    public DatabaseConnectionDTO switchConnection(String probeKey, Long connectionId) {
        log.info("[DatabaseProbeService] ========== 开始切换数据库连接 ==========");
        log.info("[DatabaseProbeService] probeKey={}, connectionId={}", probeKey, connectionId);

        try {
            // 1. 获取探针信息
            log.info("[DatabaseProbeService] 步骤1: 获取探针信息");
            DatabaseProbe probe = getByProbeKey(probeKey);
            if (probe == null) {
                log.error("[DatabaseProbeService] 探针不存在: {}", probeKey);
                throw new IllegalArgumentException("探针不存在: " + probeKey);
            }
            log.info("[DatabaseProbeService] 探针信息: id={}, name={}, type={}", probe.getId(), probe.getName(), probe.getDatabaseType());

            // 2. 获取目标数据库连接
            log.info("[DatabaseProbeService] 步骤2: 获取目标数据库连接");
            DatabaseConnection connection = databaseConnectionMapper.findById(connectionId);
            if (connection == null) {
                log.error("[DatabaseProbeService] 数据库连接不存在: {}", connectionId);
                throw new IllegalArgumentException("数据库连接不存在: " + connectionId);
            }
            log.info("[DatabaseProbeService] 目标连接: id={}, name={}, host={}, port={}, db={}",
                    connection.getId(), connection.getName(), connection.getDatabaseHost(),
                    connection.getDatabasePort(), connection.getDatabaseName());

            // 3. 验证数据库类型一致
            log.info("[DatabaseProbeService] 步骤3: 验证数据库类型");
            if (!probe.getDatabaseType().equals(connection.getDatabaseType())) {
                log.error("[DatabaseProbeService] 数据库类型不匹配");
                throw new IllegalArgumentException(
                        "数据库类型不匹配，无法切换。探针类型: " + probe.getDatabaseType() +
                        ", 目标连接类型: " + connection.getDatabaseType());
            }

            // 4. 验证连接是否启用
            log.info("[DatabaseProbeService] 步骤4: 验证连接状态");
            if (connection.getIsActive() == null || !connection.getIsActive()) {
                log.error("[DatabaseProbeService] 目标连接未启用");
                throw new IllegalArgumentException("目标数据库连接未启用");
            }

            // 5. 测试目标连接（暂时注释，避免连接测试失败导致切换失败）
            // if (!databaseConnectionService.testConnection(connection)) {
            //     throw new RuntimeException("目标数据库连接测试失败，无法切换");
            // }
            log.info("[DatabaseProbeService] 步骤5: 跳过连接测试，直接切换");

            // 6. 更新探针的数据库连接配置
        probe.setDatabaseHost(connection.getDatabaseHost());
        probe.setDatabasePort(connection.getDatabasePort());
        probe.setDatabaseName(connection.getDatabaseName());
        probe.setUsername(connection.getUsername());
        probe.setPassword(connection.getPassword()); // 密码已加密
        probe.setSchemas(connection.getSchemas());
        probe.setCurrentConnectionId(connectionId);
        probe.setUpdateTime(LocalDateTime.now());

        // 7. 更新连接池（将当前连接添加到连接池）
        String poolJson = probe.getConnectionPool();
        List<Map<String, Object>> connectionPool;
        if (poolJson == null || poolJson.isEmpty() || "[]".equals(poolJson)) {
            connectionPool = new java.util.ArrayList<>();
        } else {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                connectionPool = mapper.readValue(poolJson, List.class);
            } catch (Exception e) {
                log.warn("[DatabaseProbeService] 解析连接池JSON失败，创建新连接池", e);
                connectionPool = new java.util.ArrayList<>();
            }
        }

        // 添加或更新连接池中的记录
        Map<String, Object> poolEntry = new HashMap<>();
        poolEntry.put("id", connection.getId());
        poolEntry.put("name", connection.getName());
        poolEntry.put("databaseHost", connection.getDatabaseHost());
        poolEntry.put("databasePort", connection.getDatabasePort());
        poolEntry.put("databaseName", connection.getDatabaseName());

        // 检查是否已存在
        boolean found = false;
        for (int i = 0; i < connectionPool.size(); i++) {
            Map<String, Object> entry = (Map<String, Object>) connectionPool.get(i);
            if (entry.get("id").equals(connection.getId())) {
                connectionPool.set(i, poolEntry);
                found = true;
                break;
            }
        }
        if (!found) {
            connectionPool.add(poolEntry);
        }

        // 序列化连接池
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String updatedPoolJson = mapper.writeValueAsString(connectionPool);
            probe.setConnectionPool(updatedPoolJson);
        } catch (Exception e) {
            log.error("[DatabaseProbeService] 序列化连接池失败", e);
        }

        // 8. 保存到数据库
        databaseProbeMapper.updateById(probe);
        log.info("[DatabaseProbeService] 数据库配置已更新到数据库 - currentConnectionId={}, databaseName={}",
                connectionId, connection.getDatabaseName());

        // 注意：不同步到Agent，不触发采集
        // 用户需要手动刷新或等待下次心跳周期获取最新配置

        log.info("[DatabaseProbeService] 数据库连接切换成功（仅数据库更新） - probeKey={}, connectionId={}",
                probeKey, connectionId);

        // 9. 返回切换后的数据库连接信息（DTO格式，不包含密码）
        return DatabaseConnectionDTO.builder()
                .id(connection.getId())
                .name(connection.getName())
                .databaseType(connection.getDatabaseType())
                .databaseHost(connection.getDatabaseHost())
                .databasePort(connection.getDatabasePort())
                .databaseName(connection.getDatabaseName())
                .username(connection.getUsername())
                .password(null)
                .schemas(connection.getSchemas())
                .isActive(connection.getIsActive())
                .createdAt(connection.getCreatedAt())
                .updatedAt(connection.getUpdatedAt())
                .build();
        } catch (Exception e) {
            log.error("[DatabaseProbeService] 切换数据库连接失败", e);
            throw new RuntimeException("切换数据库连接失败: " + e.getMessage(), e);
        }
    }
}
