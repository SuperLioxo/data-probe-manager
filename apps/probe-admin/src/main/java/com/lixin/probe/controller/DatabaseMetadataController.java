package com.lixin.probe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.dto.DatabaseConnectionDTO;
import com.lixin.probe.entity.ColumnInfo;
import com.lixin.probe.entity.DatabaseConnection;
import com.lixin.probe.entity.DatabaseMetadata;
import com.lixin.probe.entity.DatabasePerformance;
import com.lixin.probe.entity.TableInfo;
import com.lixin.probe.mapper.DatabaseConnectionMapper;
import com.lixin.probe.mapper.DatabaseProbeMapper;
import com.lixin.probe.service.DatabaseConnectionService;
import com.lixin.probe.service.DatabaseMetadataService;
import com.lixin.probe.service.DatabasePerformanceService;
import com.lixin.probe.service.DatabaseProbeService;
import com.lixin.probe.service.ProbeService;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ValidationUtil;
import com.lixin.probe.websocket.MetaProbeWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据库元数据查询Controller
 * 负责数据库表结构浏览、数据查询、连接管理等元数据相关操作
 */
@RestController
@RequestMapping("/api/database-metadata")
public class DatabaseMetadataController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseMetadataController.class);

    @Autowired
    private DatabaseMetadataService metadataService;

    @Autowired
    private DatabasePerformanceService performanceService;

    @Autowired(required = false)
    private MetaProbeWebSocketHandler webSocketHandler;

    @Autowired
    private com.lixin.probe.service.ProbeStatusValidationService statusValidationService;

    @Autowired
    private DatabaseProbeService databaseProbeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.lixin.probe.mapper.DatabaseConnectionMapper databaseConnectionMapper;

    @Autowired
    private DatabaseProbeMapper databaseProbeMapper;

    @Autowired
    private ProbeService probeService;

    @Autowired
    private DatabaseConnectionService databaseConnectionService;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取数据库探针信息（包含 currentConnectionId）
     *
     * @param probeKey 探针KEY
     * @return 数据库探针信息
     */
    @GetMapping("/{probeKey}/info")
    public Result<com.lixin.probe.entity.DatabaseProbe> getProbeInfo(@PathVariable String probeKey) {
        log.info("获取数据库探针信息: probeKey={}", probeKey);

        return ControllerHelper.safeGet(() -> {
            com.lixin.probe.entity.DatabaseProbe databaseProbe =
                databaseProbeService.getByProbeKey(probeKey);

            if (databaseProbe == null) {
                log.warn("数据库探针不存在: probeKey={}", probeKey);
                throw new IllegalArgumentException("数据库探针不存在: " + probeKey);
            }

            log.info("获取数据库探针信息成功: probeKey={}, currentConnectionId={}",
                     probeKey, databaseProbe.getCurrentConnectionId());
            return databaseProbe;
        }, "获取数据库探针信息失败");
    }

    /**
     * 获取数据库元数据（包括基本信息、性能指标、表统计）
     *
     * @param probeKey 探针KEY
     * @param instanceId 数据库实例ID（可选）
     * @return 元数据
     */
    @GetMapping("/{probeKey}/metadata")
    public Result<Map<String, Object>> getMetadata(
            @PathVariable String probeKey,
            @RequestParam(required = false) String instanceId) {
        log.info("========== [getMetadata] 开始获取数据库元数据 ==========");
        log.info("probeKey={}, instanceId={}", probeKey, instanceId);

        // 使用ValidationUtil验证probeKey
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            log.error("✗ 参数验证失败: {}", error.getMessage());
            return Result.error(error.getMessage());
        }
        log.info("✓ 参数验证通过");

        // 转换probeKey格式：将probe表中的probeKey映射到database_probe表的格式
        String effectiveProbeKey = convertProbeKeyForDatabase(probeKey);
        if (!effectiveProbeKey.equals(probeKey)) {
            log.info("  probeKey转换: {} -> {}", probeKey, effectiveProbeKey);
        }

        // 验证探针是否在线（仅用于前端状态展示，不阻止数据查询）
        log.info("步骤1: 检查探针在线状态...");
        boolean isOnline = false;
        try {
            com.lixin.probe.entity.DatabaseProbe dbProbe = databaseProbeMapper.selectOne(
                new LambdaQueryWrapper<com.lixin.probe.entity.DatabaseProbe>()
                    .eq(com.lixin.probe.entity.DatabaseProbe::getProbeKey, probeKey)
            );

            if (dbProbe != null) {
                boolean statusOnline = "online".equalsIgnoreCase(dbProbe.getStatus());
                boolean recentHeartbeat = false;
                if (dbProbe.getLastHeartbeat() != null) {
                    long heartbeatAge = System.currentTimeMillis() -
                        dbProbe.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    recentHeartbeat = heartbeatAge < 5 * 60 * 1000;
                }
                isOnline = statusOnline && recentHeartbeat;
            } else {
                isOnline = statusValidationService.isProbeOnline(probeKey);
            }
        } catch (Exception e) {
            log.error("检查探针状态失败", e);
        }
        log.info("  探针在线状态: {} (仅展示，不阻止查询)", isOnline ? "在线" : "离线");

        try {
            Map<String, Object> result = new HashMap<>();

            // 数据库基本信息
            log.info("步骤2: 查询数据库基本信息...");
            log.info("  instanceId={}", instanceId);

            // 根据instanceId获取对应的数据库元数据
            DatabaseMetadata metadata = null;
            if (instanceId != null && !instanceId.isEmpty()) {
                // 查询指定数据库的元数据
                log.info("  查询指定数据库的元数据: databaseName={}", instanceId);
                // 根据databaseName获取最新的匹配元数据
                metadata = metadataService.getLatestByProbeKeyAndDatabase(effectiveProbeKey, instanceId);

                if (metadata == null) {
                    log.warn("  ⚠️  未找到数据库 {} 的元数据，请先采集元数据", instanceId);
                }
            } else {
                // 没有指定instanceId，获取最新的元数据
                metadata = metadataService.getLatestByProbeKey(effectiveProbeKey);
            }

            Map<String, Object> databaseInfo = new HashMap<>();
            if (metadata != null) {
                log.info("  找到数据库元数据:");
                log.info("    - databaseType: {}", metadata.getDatabaseType());
                log.info("    - databaseName: {}", metadata.getDatabaseName());
                log.info("    - version: {}", metadata.getVersion());
                log.info("    - charset: {}", metadata.getCharset());
                log.info("    - collation: {}", metadata.getCollation());
                log.info("    - url: {}", metadata.getUrl());

                databaseInfo.put("type", metadata.getDatabaseType());
                databaseInfo.put("version", metadata.getVersion());
                databaseInfo.put("databaseName", metadata.getDatabaseName());
                databaseInfo.put("charset", metadata.getCharset());
                databaseInfo.put("collation", metadata.getCollation());
                databaseInfo.put("url", metadata.getUrl());
                databaseInfo.put("instanceId", instanceId); // 添加instanceId用于前端显示
            } else {
                log.warn("  ⚠️  数据库元数据为空: probeKey={}", probeKey);
                log.warn("  提示: 请检查Agent是否已采集元数据");
                databaseInfo.put("instanceId", instanceId); // 即使为空也传递instanceId
            }
            result.put("databaseInfo", databaseInfo);
            log.info("✓ 数据库基本信息查询完成");

            // 性能指标
            log.info("步骤3: 查询性能指标...");
            DatabasePerformance performance = performanceService.getLatestByProbeKey(effectiveProbeKey);
            Map<String, Object> metrics = new HashMap<>();
            if (performance != null) {
                log.info("  找到性能指标:");
                log.info("    - connectionCount: {}", performance.getConnectionCount());
                log.info("    - qps: {}", performance.getQps());
                log.info("    - tps: {}", performance.getTps());
                log.info("    - timestamp: {}", performance.getTimestamp());

                metrics.put("connectionCount", performance.getConnectionCount());
                metrics.put("activeConnections", performance.getActiveConnections());
                metrics.put("maxConnections", performance.getMaxConnections());
                metrics.put("connectionUsage", performance.getConnectionUsage());
                metrics.put("qps", performance.getQps());
                metrics.put("tps", performance.getTps());
                metrics.put("cacheHitRate", performance.getCacheHitRate());
                metrics.put("avgQueryTime", performance.getAvgQueryTime());
                metrics.put("slowQueryCount", performance.getSlowQueryCount());
                metrics.put("timestamp", performance.getTimestamp());
            } else {
                log.warn("  ⚠️  性能指标为空: probeKey={}", probeKey);
            }
            result.put("performance", metrics);
            log.info("✓ 性能指标查询完成");

            // 表统计数据
            log.info("步骤4: 查询表统计数据...");
            log.info("  instanceId={}, 将使用此参数过滤表数据", instanceId);

            // 根据instanceId获取真实的databaseName
            String databaseName = null;
            if (instanceId != null && !instanceId.isEmpty()) {
                try {
                    // instanceId是DatabaseConnection的ID，需要查找对应的databaseName
                    Long connectionId = Long.parseLong(instanceId);
                    DatabaseConnection connection = databaseConnectionService.getConnectionById(connectionId);
                    if (connection != null) {
                        databaseName = connection.getDatabaseName();
                        log.info("  根据instanceId={} 找到数据库连接: databaseName={}", instanceId, databaseName);
                    } else {
                        log.warn("  未找到ID={}的数据库连接，使用instanceId作为databaseName", instanceId);
                        databaseName = instanceId;
                    }
                } catch (NumberFormatException e) {
                    log.info("  instanceId不是数字，直接作为databaseName使用: {}", instanceId);
                    databaseName = instanceId;
                }
            }

            try {
                // 查询所有表的统计数据，不分页，支持databaseName过滤
                log.info("  使用 databaseName={} 过滤表数据", databaseName);
                Page<TableInfo> tablesPage = metadataService.getTables(effectiveProbeKey, databaseName, 1, 10000, null);
                if (tablesPage != null && tablesPage.getRecords() != null && !tablesPage.getRecords().isEmpty()) {
                    log.info("  找到 {} 个表的统计数据:", tablesPage.getRecords().size());

                    // 转换为前端期望的格式
                    List<Map<String, Object>> tableStats = new java.util.ArrayList<>();
                    for (TableInfo table : tablesPage.getRecords()) {
                        Map<String, Object> tableStat = new HashMap<>();
                        tableStat.put("tableName", table.getTableName());
                        tableStat.put("tableComment", ""); // TableInfo没有此字段，使用空字符串
                        tableStat.put("rowCount", table.getRowCount());
                        tableStat.put("dataSize", table.getDataSize());
                        tableStat.put("columnCount", 0); // TableInfo没有此字段，使用0
                        tableStat.put("indexCount", 0); // TableInfo没有此字段，使用0
                        tableStat.put("createTime", table.getCreateTime());
                        tableStat.put("updateTime", table.getUpdateTime());
                        tableStats.add(tableStat);

                        // 打印前5个表的信息
                        if (tableStats.size() <= 5) {
                            log.info("    - 表名: {}, 行数: {}, 大小: {}",
                                table.getTableName(), table.getRowCount(), table.getDataSize());
                        }
                    }

                    result.put("tableStats", tableStats);
                    log.info("✓ 表统计数据查询完成: 共 {} 个表", tableStats.size());
                } else {
                    log.warn("  ⚠️  未找到表统计数据: probeKey={}", probeKey);
                    result.put("tableStats", new java.util.ArrayList<>());
                }
            } catch (Exception e) {
                log.error("  ✗ 查询表统计数据失败: probeKey={}", probeKey, e);
                result.put("tableStats", new java.util.ArrayList<>());
            }

            log.info("✓ 获取数据库元数据成功: probeKey={}", probeKey);
            log.info("====================================================");
            return Result.success(result);
        } catch (Exception e) {
            log.error("✗ 获取数据库元数据失败: probeKey={}", probeKey, e);
            log.error("错误详情: {}", e.getMessage(), e);
            log.info("====================================================");
            return Result.error("获取数据库元数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据库表列表
     *
     * @param probeKey 探针KEY
     * @param instanceId 数据库实例ID（可选）
     * @param schema    Schema名称（可选）
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @return 表列表
     */
    @GetMapping("/{probeKey}/tables")
    public Result<Page<TableInfo>> getTables(
            @PathVariable String probeKey,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String schema,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize) {

        // 使用ValidationUtil验证参数
        Result<Void> error = ValidationUtil.validatePageParams(pageNum, pageSize);
        if (error != null) {
            return Result.error(error.getMessage());
        }

        log.info("获取数据库表列表: probeKey={}, instanceId={}, schema={}, pageNum={}, pageSize={}",
                probeKey, instanceId, schema, pageNum, pageSize);

        return ControllerHelper.safeGet(() -> {
            String databaseName = instanceId;
            Page<TableInfo> page = metadataService.getTables(probeKey, databaseName, pageNum, pageSize, null);
            log.info("获取数据库表列表成功: probeKey={}, databaseName={}, total={}, records={}",
                     probeKey, databaseName, page.getTotal(), page.getRecords().size());
            return page;
        }, "获取数据库表列表失败");
    }

    /**
     * 获取表结构详情（包括列信息）
     *
     * @param probeKey   探针KEY
     * @param tableName 表名
     * @return 表结构详情
     */
    @GetMapping("/{probeKey}/tables/{tableName}/structure")
    public Result<Map<String, Object>> getTableStructure(
            @PathVariable String probeKey,
            @PathVariable String tableName,
            @RequestParam(required = false) String instanceId) {

        log.info("获取表结构详情: probeKey={}, tableName={}, instanceId={}", probeKey, tableName, instanceId);

        return ControllerHelper.safeGet(() -> {
            Map<String, Object> result = new HashMap<>();

            // Map instanceId to databaseName
            String databaseName = instanceId;

            // 表基本信息 - 从表列表中查找
            Page<TableInfo> tablesPage = metadataService.getTables(probeKey, databaseName, 1, 1000, tableName);
            TableInfo tableInfo = null;
            if (tablesPage != null && tablesPage.getRecords() != null) {
                tableInfo = tablesPage.getRecords().stream()
                        .filter(t -> t.getTableName().equals(tableName))
                        .filter(t -> databaseName == null || databaseName.equals(t.getDatabaseName()))
                        .findFirst()
                        .orElse(null);
            }

            if (tableInfo == null) {
                log.warn("表不存在: probeKey={}, databaseName={}, tableName={}", probeKey, databaseName, tableName);
                // 继续执行，仍然返回列信息
            } else {
                result.put("tableInfo", tableInfo);
            }

            // 列信息
            List<ColumnInfo> columns = metadataService.getTableStructure(probeKey, databaseName, tableName);
            result.put("columns", columns);

            log.info("获取表结构详情成功: probeKey={}, databaseName={}, tableName={}, columns={}",
                    probeKey, databaseName, tableName, columns != null ? columns.size() : 0);
            return result;
        }, "获取表结构详情失败");
    }

    /**
     * 查询表数据（分页）
     *
     * @param probeKey 探针KEY
     * @param tableName 表名
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 表数据
     */
    @GetMapping("/{probeKey}/tables/{tableName}/data")
    public Result<Map<String, Object>> queryTableData(
            @PathVariable String probeKey,
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String databaseName) {

        // 使用ValidationUtil验证参数
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        // 验证探针是否在线（仅用于前端状态展示，不阻止数据查询）
        boolean isOnline = statusValidationService.isProbeOnline(probeKey);
        log.info("探针在线状态: {} (仅展示，不阻止查询)", isOnline ? "在线" : "离线");

        error = ValidationUtil.validateNotEmpty(tableName, "表名");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        error = ValidationUtil.validatePageParams(pageNum, pageSize);
        if (error != null) {
            return Result.error(error.getMessage());
        }

        if (pageSize > 1000) {
            return Result.error("每页数量不能超过1000");
        }

        if (pageNum > 10000) {
            return Result.error("页码不能超过10000");
        }

        // ⭐ 转换probeKey格式：将probe表中的probeKey映射到database_probe表的格式
        String effectiveProbeKey = convertProbeKeyForDatabase(probeKey);
        if (!effectiveProbeKey.equals(probeKey)) {
            log.info("  probeKey转换: {} -> {}", probeKey, effectiveProbeKey);
        }

        log.info("查询表数据: probeKey={}, effectiveProbeKey={}, databaseName={}, tableName={}, pageNum={}, pageSize={}",
                probeKey, effectiveProbeKey, databaseName, tableName, pageNum, pageSize);

        return ControllerHelper.safeGet(
                () -> metadataService.queryTableData(effectiveProbeKey, databaseName, tableName, pageNum, pageSize),
                "查询表数据失败"
        );
    }

    /**
     * 切换数据库连接
     */
    @PostMapping("/{probeKey}/switch-connection")
    public Result<String> switchConnection(
            @PathVariable String probeKey,
            @RequestParam String connectionId) {

        log.info("切换数据库连接: probeKey={}, connectionId={}", probeKey, connectionId);

        return ControllerHelper.safeExecute(() -> {
            // 1. 查询连接信息
            String sql = "SELECT database_host, database_port, database_name, username, password, schemas, current_connection_id " +
                         "FROM database_connection " +
                         "WHERE probe_key = ? AND id = ?";

            Map<String, Object> connInfo = jdbcTemplate.queryForMap(sql, probeKey, connectionId);
            if (connInfo == null || connInfo.isEmpty()) {
                log.warn("切换连接失败：连接信息不存在: probeKey={}, connectionId={}",
                         probeKey, connectionId);
                throw new IllegalArgumentException("连接信息不存在");
            }

            // 2. 构建更新SQL
            String schemasStr = connInfo.get("schemas") != null ?
                    connInfo.get("schemas").toString() : "[]";

            // 如果schemas是字符串形式的 "[public, test]"，需要转换为数组格式
            if (schemasStr.startsWith("[")) {
                schemasStr = schemasStr.replace("[", "{")
                                   .replace("]", "}")
                                   .replace(", ", ",");
            }

            // 使用参数化查询防止 SQL 注入
            String updateSql = "UPDATE database_connection " +
                    "SET database_host = ?, " +
                    "    database_port = ?, " +
                    "    database_name = ?, " +
                    "    username = ?, " +
                    "    password = ?, " +
                    "    schemas = ?, " +
                    "    current_connection_id = ?, " +
                    "    update_time = NOW() " +
                    "WHERE probe_key = ?";

            log.info("[切换连接] 更新SQL: probeKey={}, connectionId={}", probeKey, connectionId);
            int updated = jdbcTemplate.update(
                    updateSql,
                    connInfo.get("database_host"),
                    connInfo.get("database_port"),
                    connInfo.get("database_name"),
                    connInfo.get("username"),
                    connInfo.get("password"),
                    schemasStr,
                    connectionId,
                    probeKey
            );
            log.info("[切换连接] 更新行数: {}", updated);

            if (updated > 0) {
                log.info("[切换连接] 成功 - 已切换到: {}", connInfo.get("database_name"));
            } else {
                log.warn("[切换连接] 探针不存在: {}", probeKey);
                throw new IllegalArgumentException("探针不存在: " + probeKey);
            }

        }, "切换连接成功", "切换连接失败");
    }

    /**
     * 测试数据库实例连接
     *
     * @param probeKey 探针KEY
     * @param instanceId 实例ID
     * @return 测试结果
     */
    @PostMapping("/{probeKey}/instances/{instanceId}/test")
    public Result<String> testInstanceConnection(
            @PathVariable String probeKey,
            @PathVariable String instanceId) {
        log.info("测试数据库连接: probeKey={}, instanceId={}", probeKey, instanceId);

        return ControllerHelper.safeExecute(() -> {
            // 1. 获取Agent URL
            String agentUrl = getAgentUrlByProbeKey(probeKey);

            // 2. 调用Agent的测试连接API
            String testUrl = String.format("%s/agent/database/instances/%s/test",
                                          agentUrl, instanceId);

            log.info("调用Agent测试连接API: url={}", testUrl);

            try {
                // 使用RestTemplate调用Agent API
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(
                    testUrl, null, Map.class);

                if (response == null) {
                    throw new RuntimeException("Agent返回空响应");
                }

                Boolean success = (Boolean) response.get("success");
                String message = (String) response.get("message");

                if (Boolean.TRUE.equals(success)) {
                    log.info("数据库连接测试成功: probeKey={}, instanceId={}",
                             probeKey, instanceId);
                    // Don't return anything here - ControllerHelper will handle it
                } else {
                    throw new RuntimeException(message != null ? message : "连接测试失败");
                }

            } catch (Exception e) {
            log.error("调用Agent测试连接API失败: probeKey={}, instanceId={}",
                     probeKey, instanceId, e);
            throw e;
        }

        }, "连接测试成功", "连接测试失败");
    }

    /**
     * 切换数据库实例并触发采集
     *
     * @param probeKey 探针KEY
     * @param instanceId 实例ID
     * @return 切换结果
     */
    @PostMapping("/{probeKey}/switch-instance")
    public Result<Map<String, Object>> switchInstance(
            @PathVariable String probeKey,
            @RequestParam String instanceId) {
        log.info("切换数据库实例: probeKey={}, instanceId={}", probeKey, instanceId);

        return ControllerHelper.safeGet(() -> {
            // 1. 验证instanceId并获取数据库连接信息
            Long connectionId;
            try {
                connectionId = Long.parseLong(instanceId);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("无效的实例ID: " + instanceId);
            }

            DatabaseConnection connection = databaseConnectionMapper.selectById(connectionId);
            if (connection == null) {
                throw new IllegalArgumentException("数据库实例不存在: ID=" + instanceId);
            }

            if (!Boolean.TRUE.equals(connection.getIsActive())) {
                throw new IllegalArgumentException("数据库实例未启用: " + connection.getName());
            }

            log.info("找到数据库连接: name={}, databaseName={}, host={}, port={}",
                    connection.getName(), connection.getDatabaseName(),
                    connection.getDatabaseHost(), connection.getDatabasePort());

            // 2. 提取Agent代码并检查在线状态
            String agentCode = extractAgentCodeFromProbeKey(probeKey);
            log.info("提取的Agent代码: {}", agentCode);

            // 3. 通过WebSocket触发采集（使用实际的probeKey）
            log.info("触发数据库元数据采集: probeKey={}, instanceId={}", probeKey, instanceId);

            if (webSocketHandler != null && webSocketHandler.isOnline(agentCode)) {
                boolean sent = webSocketHandler.sendCollectCommand(probeKey, "DATABASE");
                if (!sent) {
                    throw new RuntimeException("采集命令发送失败");
                }
            } else {
                throw new IllegalArgumentException("Agent离线或WebSocket不可用: " + agentCode);
            }

            // 4. 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("instanceId", instanceId);
            result.put("databaseName", connection.getDatabaseName());
            result.put("message", "切换成功，正在采集数据...");

            return result;

        }, "切换实例失败");
    }

    /**
     * 扫描新数据库
     *
     * @param probeKey 探针KEY
     * @return 扫描结果
     */
    @PostMapping("/{probeKey}/scan-databases")
    public Result<Map<String, Object>> scanNewDatabases(@PathVariable String probeKey) {
        log.info("========== 扫描新数据库: probeKey={} ==========", probeKey);

        return ControllerHelper.safeGet(() -> {
            // 1. 获取数据库类型
            String databaseType = extractDatabaseType(probeKey);
            if (databaseType == null) {
                throw new IllegalArgumentException("无法确定数据库类型");
            }

            log.info("数据库类型: {}", databaseType);

            // 2. 获取Agent URL
            String agentUrl = getAgentUrlByProbeKey(probeKey);
            String scanUrl = agentUrl + "/api/database/scan?databaseType=" + databaseType;

            log.info("调用Agent扫描API: {}", scanUrl);

            // 3. 调用Agent扫描API
            Map<String, Object> scanResult;
            try {
                scanResult = restTemplate.getForObject(scanUrl, Map.class);
            } catch (Exception e) {
                log.error("调用Agent扫描API失败", e);
                throw new RuntimeException("扫描失败: " + e.getMessage());
            }

            if (scanResult == null || !Boolean.TRUE.equals(scanResult.get("success"))) {
                String message = scanResult != null ? (String) scanResult.get("message") : "扫描失败";
                throw new RuntimeException(message);
            }

            // 4. 处理扫描结果
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newDatabases =
                (List<Map<String, Object>>) scanResult.get("newDatabases");

            log.info("发现 {} 个新数据库", newDatabases.size());

            // 5. 将新数据库添加到配置
            int addedCount = 0;
            List<String> addedDatabaseNames = new ArrayList<>();

            for (Map<String, Object> dbInfo : newDatabases) {
                try {
                    // 直接添加到database_connection表
                    String dbName = (String) dbInfo.get("databaseName");
                    DatabaseConnection newConnection = new DatabaseConnection();
                    newConnection.setName((String) dbInfo.get("description"));
                    newConnection.setDatabaseType((String) dbInfo.get("databaseType"));
                    newConnection.setDatabaseHost((String) dbInfo.get("host"));
                    newConnection.setDatabasePort((Integer) dbInfo.get("port"));
                    newConnection.setDatabaseName(dbName);
                    newConnection.setUsername((String) dbInfo.get("username"));
                    // 密码需要从现有配置中获取
                    newConnection.setSchemas(null);
                    newConnection.setIsActive(true);

                    databaseConnectionMapper.insert(newConnection);
                    addedCount++;
                    addedDatabaseNames.add(dbName);
                    log.info("✓ 已添加数据库配置: {}", dbName);
                } catch (Exception e) {
                    log.error("添加数据库配置失败: {}", dbInfo.get("databaseName"), e);
                }
            }

            // 6. 构建响应
            Map<String, Object> result = new HashMap<>();
            result.put("newDatabases", newDatabases);
            result.put("addedCount", addedCount);
            result.put("addedDatabaseNames", addedDatabaseNames);
            result.put("message", String.format("扫描完成，发现 %d 个新数据库，已添加 %d 个",
                newDatabases.size(), addedCount));

            log.info("========== 扫描完成: 发现 {} 个，添加 {} 个 ==========",
                     newDatabases.size(), addedCount);

            return result;

        }, "扫描新数据库失败");
    }

    /**
     * 提取数据库类型
     */
    private String extractDatabaseType(String probeKey) {
        // AGENT-postgresql → postgresql
        // AGENT-mysql → mysql
        if (probeKey.matches("^AGENT-[a-z]+$")) {
            String[] parts = probeKey.split("-");
            return parts[1];
        }
        return null;
    }

    /**
     * 根据probeKey获取Agent的URL
     *
     * @param probeKey 探针KEY
     * @return Agent URL
     */
    private String getAgentUrlByProbeKey(String probeKey) {
        try {
            // 从probe表获取探针信息
            Probe probe = probeService.getByProbeKey(probeKey);
            if (probe == null) {
                log.warn("探针不存在: probeKey={}", probeKey);
                return "http://localhost:58081";  // 默认地址
            }

            String host = probe.getHostIp();
            Integer port = probe.getPort();

            if (host == null || host.isEmpty()) {
                host = "localhost";
            }
            if (port == null) {
                port = 58081;
            }

            String agentUrl = String.format("http://%s:%d", host, port);
            log.info("从probe表获取Agent URL: probeKey={}, url={}", probeKey, agentUrl);
            return agentUrl;

        } catch (Exception e) {
            log.error("获取Agent URL失败，使用默认地址: probeKey={}", probeKey, e);
            return "http://localhost:58081";
        }
    }

    /**
     * 转换probeKey格式：将前端传入的probeKey转换为实际使用的probeKey
     *
     * ⭐ 新架构：所有同类型数据库使用统一的probeKey
     * - AGENT-postgresql：所有PostgreSQL数据库
     * - AGENT-mysql：所有MySQL数据库
     * - AGENT-oracle：所有Oracle数据库
     *
     * @param probeKey 前端传入的probeKey
     * @return 实际使用的统一probeKey
     */
    private String convertProbeKeyForDatabase(String probeKey) {
        // ⭐ 新架构：直接返回统一的probeKey，不再转换
        // 前端应该传入 AGENT-postgresql, AGENT-mysql 等
        // 如果传入旧格式（如 AGENT-database-mnlocl-als），需要转换

        if (probeKey == null || probeKey.isEmpty()) {
            return probeKey;
        }

        // 如果已经是新格式（AGENT-{type}），直接返回
        if (probeKey.matches("^AGENT-[a-z]+$")) {
            log.debug("probeKey已是新格式: {}", probeKey);
            return probeKey;
        }

        // 如果是旧格式（AGENT-database-xxx），转换为新格式
        if (probeKey.startsWith("AGENT-database-")) {
            try {
                // 从probe表查询探针信息，获取数据库类型
                Probe probe = probeService.getByProbeKey(probeKey);
                if (probe != null) {
                    // ⭐ 优先从probe.config中提取databaseType
                    String databaseType = extractDatabaseType(probe);
                    if (databaseType != null && !databaseType.isEmpty()) {
                        String newProbeKey = "AGENT-" + databaseType.toLowerCase();
                        log.info("probeKey格式转换（从config，旧→新）: {} -> {}", probeKey, newProbeKey);
                        return newProbeKey;
                    }

                    // ⭐ 降级方案：查询database_probe表获取数据库类型
                    LambdaQueryWrapper<com.lixin.probe.entity.DatabaseProbe> wrapper =
                        new LambdaQueryWrapper<>();
                    wrapper.eq(com.lixin.probe.entity.DatabaseProbe::getProbeKey, probeKey);
                    com.lixin.probe.entity.DatabaseProbe dbProbe = databaseProbeMapper.selectOne(wrapper);

                    if (dbProbe != null) {
                        String dbType = dbProbe.getDatabaseType();
                        String newProbeKey = "AGENT-" + dbType.toLowerCase();
                        log.info("probeKey格式转换（从database_probe，旧→新）: {} -> {}", probeKey, newProbeKey);
                        return newProbeKey;
                    }
                }
            } catch (Exception e) {
                log.warn("转换probeKey失败，使用原值: {}", probeKey, e);
            }
        }

        return probeKey;
    }

    /**
     * 从探针配置中提取数据库类型
     *
     * @param probe 探针对象
     * @return 数据库类型
     */
    private String extractDatabaseType(Probe probe) {
        try {
            String configJson = probe.getConfig();
            if (configJson == null || configJson.isEmpty()) {
                log.warn("探针配置为空: probeKey={}", probe.getProbeKey());
                return null;
            }

            // 解析JSON配置，获取databaseType字段
            Map<String, Object> config = objectMapper.readValue(configJson, Map.class);
            String databaseType = (String) config.get("databaseType");

            log.info("从探针配置提取数据库类型: probeKey={}, databaseType={}",
                     probe.getProbeKey(), databaseType);
            return databaseType;

        } catch (Exception e) {
            log.error("解析探针配置失败: probeKey={}", probe.getProbeKey(), e);
            return null;
        }
    }

    /**
     * 从probeKey提取数据库类型
     * 例如: AGENT-postgresql -> postgresql
     *      AGENT-mysql -> mysql
     *
     * @param probeKey 探针KEY
     * @return 数据库类型（小写）
     */
    private String extractDatabaseTypeFromProbeKey(String probeKey) {
        if (probeKey == null || !probeKey.startsWith("AGENT-")) {
            return null;
        }

        // 提取 AGENT- 后面的部分并转为小写
        String databaseType = probeKey.substring("AGENT-".length()).toLowerCase();

        // 标准化数据库名称（postgresql -> postgresql, mysql -> mysql）
        return databaseType;
    }

    /**
     * 从probeKey中提取Agent代码
     * <p>
     * 支持格式：
     * - 统一格式：AGENT-postgresql → AGENT
     * - 旧格式：AGENT-database-xxx → AGENT
     *
     * @param probeKey 探针KEY
     * @return Agent代码
     */
    private String extractAgentCodeFromProbeKey(String probeKey) {
        if (probeKey == null) {
            return null;
        }

        // 特殊处理：统一probeKey格式（AGENT-postgresql, AGENT-mysql等）
        if (probeKey.matches("^AGENT-[a-z]+$")) {
            String[] parts = probeKey.split("-");
            log.debug("从统一probeKey提取Agent代码: {} → {}", probeKey, parts[0]);
            return parts[0];
        }

        // 旧格式处理：AGENT-database-xxx
        String[] parts = probeKey.split("-");
        if (parts.length >= 2 && "AGENT".equals(parts[0])) {
            log.debug("从旧格式probeKey提取Agent代码: {} → {}", probeKey, parts[0]);
            return parts[0];
        }

        log.warn("无法从probeKey提取Agent代码: {}", probeKey);
        return null;
    }
}
