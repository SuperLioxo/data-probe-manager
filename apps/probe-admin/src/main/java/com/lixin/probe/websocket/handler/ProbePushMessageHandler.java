package com.lixin.probe.websocket.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lixin.probe.entity.TableInfo;
import com.lixin.probe.entity.DatabaseProbe;
import com.lixin.probe.mapper.TableInfoMapper;
import com.lixin.probe.mapper.DatabaseProbeMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.service.DatabasePerformanceService;
import com.lixin.probe.service.FileMetadataService;
import com.lixin.probe.service.ProbeService;
import com.lixin.probe.service.ProbeStatusValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 探针推送数据消息处理器
 * 处理PROBE_PUSH和FILE_PROBE_PUSH命令
 */
@Component
public class ProbePushMessageHandler implements MessageHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbePushMessageHandler.class);

    @Autowired(required = false)
    private FileMetadataService fileMetadataService;

    @Autowired(required = false)
    private DatabasePerformanceService databasePerformanceService;

    @Autowired(required = false)
    private ProbeService probeService;

    @Autowired(required = false)
    private ProbeMapper probeMapper;

    @Autowired(required = false)
    private TableInfoMapper tableInfoMapper;

    @Autowired(required = false)
    private com.lixin.probe.mapper.FileProbeMapper fileProbeMapper;

    @Autowired(required = false)
    private DatabaseProbeMapper databaseProbeMapper;

    @Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private ProbeStatusValidationService statusValidationService;

    @Autowired(required = false)
    private com.lixin.probe.service.ChangeDetectionService changeDetectionService;

    @Autowired(required = false)
    private com.lixin.probe.mapper.SyncTaskMapper syncTaskMapper;

    @Autowired(required = false)
    private com.lixin.probe.service.AggregationService aggregationService;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.url}")
    private String adminDatasourceUrl;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.username}")
    private String adminDatasourceUsername;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.password}")
    private String adminDatasourcePassword;

    @Override
    public boolean canHandle(String type, String cmd) {
        return "PROBE_PUSH".equals(cmd) || "FILE_PROBE_PUSH".equals(cmd);
    }

    @Override
    public void handle(WebSocketSession session, String probeKey, String type, String cmd, Object payload) throws Exception {
        log.info("========== [ProbePushMessageHandler] 收到PROBE_PUSH消息 ==========");
        log.info("probeKey={}, type={}, cmd={}", probeKey, type, cmd);

        try {
            // 验证探针是否在线
            // 注意：如果probeKey是Agent的key（以"AGENT"开头），则跳过验证
            // 因为Agent是代理，它会上报多个数据库探针的数据
            if (!probeKey.equals("AGENT") && !probeKey.startsWith("AGENT-") &&
                !statusValidationService.isProbeOnline(probeKey)) {
                log.warn("拒绝离线探针的数据上报: probeKey={}, cmd={}", probeKey, cmd);
                return;
            }
            log.info("✓ 探针在线验证通过: probeKey={}", probeKey);

            if (!(payload instanceof Map)) {
                log.warn("探针推送数据格式错误: payload类型={}", payload != null ? payload.getClass() : "null");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload;
            log.info("✓ payload解析成功，包含keys: {}", data.keySet());

            // 判断数据类型并处理
            if (data.containsKey("fileInfo")) {
                // 文件探针数据
                log.info("→ 识别为文件探针数据");
                handleFileProbeData(probeKey, data);
            } else if (data.containsKey("metadata") || data.containsKey("dataSize")) {
                // 数据库探针数据
                log.info("→ 识别为数据库探针数据");
                log.info("  - 包含metadata: {}", data.containsKey("metadata"));
                log.info("  - 包含dataSize: {}", data.containsKey("dataSize"));
                handleDatabaseProbeData(probeKey, data);
            } else {
                // 通用指标数据
                log.info("→ 识别为通用指标数据");
                handleMetricData(probeKey, data);
            }

            log.info("✓ 处理探针推送数据成功: probeKey={}", probeKey);
            log.info("=====================================================");

        } catch (Exception e) {
            log.error("✗ 处理探针推送数据失败: probeKey={}", probeKey, e);
            throw e;
        }
    }

    /**
     * 处理文件探针数据
     */
    @SuppressWarnings("unchecked")
    private void handleFileProbeData(String probeKey, Map<String, Object> data) {
        try {
            Map<String, Object> fileInfo = (Map<String, Object>) data.get("fileInfo");
            if (fileInfo == null) {
                return;
            }

            // 如果probeKey是Agent的key，使用已知的文件探针key
            String targetProbeKey = probeKey;
            if (probeKey.startsWith("dev-key-") || probeKey.startsWith("AGENT-")) {
                log.info("检测到Agent推送文件数据，使用文件探针: agentKey={}", probeKey);
                // 临时方案：使用已知的文件探针key
                targetProbeKey = "AGENT-file-14iilx-0pq";
                log.info("使用文件探针: {}", targetProbeKey);
            }

            // ✅ 更新文件探针状态为online
            try {
                int updated = probeMapper.update(
                    null,
                    new LambdaUpdateWrapper<com.lixin.probe.entity.Probe>()
                        .eq(com.lixin.probe.entity.Probe::getProbeKey, targetProbeKey)
                        .set(com.lixin.probe.entity.Probe::getStatus, "online")
                        .set(com.lixin.probe.entity.Probe::getLastHeartbeat, LocalDateTime.now())
                );

                if (updated > 0) {
                    log.info("✓ 文件探针状态已更新为online: {}", targetProbeKey);
                } else {
                    log.warn("文件探针状态更新失败（可能不存在）: {}", targetProbeKey);
                }
            } catch (Exception e) {
                log.error("更新文件探针状态失败: {}", targetProbeKey, e);
            }

            // 保存文件探针数据
            if (fileMetadataService != null) {
                try {
                    fileMetadataService.saveFileMetadata(targetProbeKey, fileInfo);
                    log.info("文件探针数据已保存: probeKey={}", targetProbeKey);
                } catch (Exception e) {
                    log.error("保存文件探针数据失败: probeKey={}", targetProbeKey, e);
                }
            }

        } catch (Exception e) {
            log.error("处理文件探针数据失败: probeKey={}", probeKey, e);
        }
    }

    /**
     * 处理数据库探针数据
     */
    @SuppressWarnings("unchecked")
    private void handleDatabaseProbeData(String probeKey, Map<String, Object> data) {
        log.info("========== [handleDatabaseProbeData] 开始处理 ==========");
        log.info("probeKey={}, data.keys={}", probeKey, data.keySet());

        try {
            // 提取元数据和性能数据
            Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
            Map<String, Object> dataSize = (Map<String, Object>) data.get("dataSize");

            log.info("提取数据完成:");
            log.info("  - metadata: {}", metadata != null ? "存在 (" + metadata.keySet() + ")" : "null");
            log.info("  - dataSize: {}", dataSize != null ? "存在 (" + dataSize.keySet() + ")" : "null");

            // 如果probeKey是Agent的key（以"dev-key-"或"AGENT"开头），查找属于该Agent的探针
            List<String> targetProbeKeys = new java.util.ArrayList<>();
            if (probeKey.startsWith("dev-key-") || probeKey.equals("AGENT") || probeKey.startsWith("AGENT-")) {
                // 这是Agent用自己的key推送数据，需要找到所有属于该Agent的数据库探针
                log.info("========== [handleDatabaseProbeData] 检测到Agent推送数据 ==========");
                log.info("agentKey={}, 查找关联的数据库探针", probeKey);

                // 查找所有属于该Agent的DATABASE类型的探针
                // 如果probeKey是"AGENT"，查找所有"AGENT-database-"开头的探针
                // 如果probeKey是"AGENT-xxx"，查找所有"AGENT-xxx-database-"开头的探针
                List<com.lixin.probe.entity.Probe> allProbes = probeService.list();
                log.info("  从数据库查询到 {} 个探针", allProbes.size());

                List<com.lixin.probe.entity.Probe> databaseProbes = new java.util.ArrayList<>();

                for (com.lixin.probe.entity.Probe probe : allProbes) {
                    log.info("  检查探针: probeKey={}, type={}", probe.getProbeKey(), probe.getType());
                    if ("DATABASE".equals(probe.getType()) && probe.getProbeKey().startsWith(probeKey + "-")) {
                        log.info("    ✓ 匹配成功: {}", probe.getProbeKey());
                        databaseProbes.add(probe);
                    } else {
                        log.info("    ✗ 不匹配: type={}, startsWith={}",
                                probe.getType(), probe.getProbeKey().startsWith(probeKey + "-"));
                    }
                }

                for (com.lixin.probe.entity.Probe probe : databaseProbes) {
                    targetProbeKeys.add(probe.getProbeKey());
                }

                log.info("✓ 找到 {} 个关联的数据库探针: {}", targetProbeKeys.size(), targetProbeKeys);
            } else {
                // 这是具体探针的key，直接使用
                targetProbeKeys.add(probeKey);
                log.info("✓ 使用具体探针key: {}", probeKey);
            }

            if (targetProbeKeys.isEmpty()) {
                log.warn("⚠️  没有找到目标探针，跳过元数据保存");
                return;
            }

            // ✅ 更新所有目标探针的状态为online
            log.info("========== 更新探针状态为online ==========");
            for (String targetKey : targetProbeKeys) {
                try {
                    // 1. 更新 probe 表
                    int probeUpdated = probeMapper.update(
                        null,
                        new LambdaUpdateWrapper<com.lixin.probe.entity.Probe>()
                            .eq(com.lixin.probe.entity.Probe::getProbeKey, targetKey)
                            .set(com.lixin.probe.entity.Probe::getStatus, "online")
                            .set(com.lixin.probe.entity.Probe::getLastHeartbeat, LocalDateTime.now())
                    );

                    if (probeUpdated > 0) {
                        log.info("✓ probe表状态已更新为online: {}", targetKey);
                    } else {
                        log.warn("probe表状态更新失败（可能不存在）: {}", targetKey);
                    }

                    // 2. 同时更新 database_probe 表
                    if (databaseProbeMapper != null) {
                        int dbProbeUpdated = databaseProbeMapper.update(
                            null,
                            new LambdaUpdateWrapper<DatabaseProbe>()
                                .eq(DatabaseProbe::getProbeKey, targetKey)
                                .set(DatabaseProbe::getStatus, "online")
                                .set(DatabaseProbe::getLastHeartbeat, LocalDateTime.now())
                        );

                        if (dbProbeUpdated > 0) {
                            log.info("✓ database_probe表状态已更新为online: {}", targetKey);
                        } else {
                            log.debug("database_probe表中没有该探针（正常）: {}", targetKey);
                        }
                    }

                } catch (Exception e) {
                    log.error("更新探针状态失败: {}", targetKey, e);
                }
            }
            log.info("========== 探针状态更新完成 ==========");

            // 处理元数据：遍历所有数据库并分别保存
            if (metadata != null && databasePerformanceService != null) {
                // 获取所有数据库
                Object databasesObj = metadata.get("databases");
                if (databasesObj instanceof Map) {
                    Map<String, Object> databases = (Map<String, Object>) databasesObj;
                    log.info("========== 开始处理 {} 个数据库的元数据 ==========", databases.size());

                    for (Map.Entry<String, Object> dbEntry : databases.entrySet()) {
                        String dbKey = dbEntry.getKey();
                        Map<String, Object> database = (Map<String, Object>) dbEntry.getValue();
                        String databaseName = (String) database.get("name");
                        String databaseProbeKey = (String) database.get("probeKey");  // 获取数据库的probeKey

                        log.info("----- 处理数据库: {} (name={}, probeKey={}) -----", dbKey, databaseName, databaseProbeKey);

                        try {
                            // 为每个数据库构建单独的元数据对象
                            Map<String, Object> dbMetadata = new java.util.HashMap<>();
                            dbMetadata.put("databases", Map.of(dbKey, database));
                            dbMetadata.put("type", metadata.get("type"));

                            // 展平这个数据库的元数据
                            Map<String, Object> flattenedMetadata = flattenMetadata(dbMetadata);
                            log.info("  展平后的metadata keys: {}", flattenedMetadata.keySet());
                            log.info("  - databaseName: {}", flattenedMetadata.get("databaseName"));

                            // 使用数据库自带的probeKey保存元数据，而不是保存到所有探针
                            if (databaseProbeKey != null && !databaseProbeKey.isEmpty()) {
                                log.info("  为探针保存元数据: probeKey={}, databaseName={}", databaseProbeKey, databaseName);
                                databasePerformanceService.saveMetadata(databaseProbeKey, flattenedMetadata);
                                log.info("✓ 数据库 {} 的元数据已保存到探针 {}", databaseName, databaseProbeKey);
                            } else {
                                // 如果数据库没有probeKey，则保存到所有目标探针（兼容旧逻辑）
                                log.warn("  数据库没有probeKey，保存到所有目标探针: databaseName={}", databaseName);
                                for (String targetKey : targetProbeKeys) {
                                    log.info("  为探针保存元数据: probeKey={}, databaseName={}", targetKey, databaseName);
                                    databasePerformanceService.saveMetadata(targetKey, flattenedMetadata);
                                }
                                log.info("✓ 数据库 {} 的元数据已保存到所有目标探针", databaseName);
                            }
                        } catch (Exception e) {
                            log.error("✗ 保存数据库元数据失败: databaseName={}", databaseName, e);
                        }

                        // 自动创建同步任务
                        if (syncTaskMapper != null && databaseProbeKey != null) {
                            try {
                                autoCreateSyncTasks(databaseProbeKey, databaseName, database);
                            } catch (Exception e) {
                                log.warn("自动创建同步任务失败: databaseName={}, error={}", databaseName, e.getMessage());
                            }
                        }
                    }
                    log.info("========== 所有数据库元数据保存完成 ==========");
                }
            } else {
                log.warn("⚠️  metadata为null或databasePerformanceService未初始化");
            }

            // 保存数据库性能数据（处理每个数据库的probeKey）
            if (dataSize != null && databasePerformanceService != null) {
                Object dataSizeDatabasesObj = dataSize.get("databases");
                if (dataSizeDatabasesObj instanceof Map) {
                    Map<String, Object> dataSizeDatabases = (Map<String, Object>) dataSizeDatabasesObj;
                    log.info("========== 开始处理 {} 个数据库的数据量 ==========", dataSizeDatabases.size());

                    for (Map.Entry<String, Object> dbEntry : dataSizeDatabases.entrySet()) {
                        String dbKey = dbEntry.getKey();
                        Map<String, Object> database = (Map<String, Object>) dbEntry.getValue();
                        String databaseName = (String) database.get("name");
                        String databaseProbeKey = (String) database.get("probeKey");  // 获取数据库的probeKey

                        log.info("----- 处理数据库数据量: {} (name={}, probeKey={}) -----", dbKey, databaseName, databaseProbeKey);

                        try {
                            // 为每个数据库构建单独的数据量对象
                            Map<String, Object> dbDataSize = new java.util.HashMap<>();
                            dbDataSize.put("databases", Map.of(dbKey, database));

                            // 使用数据库自带的probeKey保存数据量，而不是保存到所有探针
                            if (databaseProbeKey != null && !databaseProbeKey.isEmpty()) {
                                Map<String, Object> flattenedDataSize = flattenDataSize(dbDataSize);
                                databasePerformanceService.savePerformanceData(databaseProbeKey, flattenedDataSize);
                                updateTablePerformanceData(databaseProbeKey, dbDataSize);
                                log.info("✓ 数据库 {} 的数据量已保存到探针 {}", databaseName, databaseProbeKey);
                            } else {
                                // 如果数据库没有probeKey，则保存到所有目标探针（兼容旧逻辑）
                                log.warn("  数据库没有probeKey，保存到所有目标探针: databaseName={}", databaseName);
                                for (String targetKey : targetProbeKeys) {
                                    Map<String, Object> flattenedDataSize = flattenDataSize(dbDataSize);
                                    databasePerformanceService.savePerformanceData(targetKey, flattenedDataSize);
                                    updateTablePerformanceData(targetKey, dbDataSize);
                                }
                                log.info("✓ 数据库 {} 的数据量已保存到所有目标探针", databaseName);
                            }
                        } catch (Exception e) {
                            log.error("✗ 保存数据库数据量失败: databaseName={}", databaseName, e);
                        }
                    }
                    log.info("========== 所有数据库数据量保存完成 ==========");
                }
            } else {
                log.warn("⚠️  dataSize为null或databasePerformanceService未初始化");
            }

            log.info("✓ 所有探针元数据处理完成");

        } catch (Exception e) {
            log.error("✗ 处理数据库探针数据失败: probeKey={}", probeKey, e);
        }
        log.info("=======================================================");
    }

    /**
     * 展平元数据结构（从嵌套的databases Map中提取第一个数据库的属性）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenMetadata(Map<String, Object> metadata) {
        Map<String, Object> flattened = new java.util.HashMap<>();

        try {
            log.debug("开始展平元数据，原始metadata keys: {}", metadata.keySet());

            // 检查是否有databases字段
            Object databasesObj = metadata.get("databases");
            log.debug("databases对象类型: {}, 值: {}", databasesObj != null ? databasesObj.getClass() : "null", databasesObj);

            if (databasesObj instanceof Map) {
                Map<String, Object> databases = (Map<String, Object>) databasesObj;
                log.info("databases map包含 {} 个数据库: {}", databases.size(), databases.keySet());

                // 获取第一个数据库
                if (!databases.isEmpty()) {
                    Map.Entry<String, Object> firstEntry = databases.entrySet().iterator().next();
                    log.info("处理数据库: {}", firstEntry.getKey());
                    Map<String, Object> database = (Map<String, Object>) firstEntry.getValue();
                    log.debug("database对象 keys: {}", database.keySet());

                    // 提取数据库属性并展平
                    String dbLevelName = (String) database.get("name");
                    flattened.put("databaseType", database.get("type"));
                    flattened.put("databaseName", dbLevelName);
                    flattened.put("version", database.get("version"));
                    flattened.put("charset", database.get("charset"));
                    flattened.put("collation", database.get("collation"));

                    // 构建URL
                    String url = buildDatabaseUrl(database);
                    flattened.put("url", url);

                    // 提取表信息
                    Object tablesObj = database.get("tables");
                    log.debug("tables对象类型: {}, 值: {}", tablesObj != null ? tablesObj.getClass() : "null", tablesObj);

                    if (tablesObj instanceof Map) {
                        Map<String, Object> tables = (Map<String, Object>) tablesObj;
                        log.info("找到 {} 个表: {}", tables.size(), tables.keySet());
                        List<Map<String, Object>> tableList = new java.util.ArrayList<>();

                        for (Map.Entry<String, Object> entry : tables.entrySet()) {
                            Map<String, Object> table = (Map<String, Object>) entry.getValue();
                            Map<String, Object> tableData = new java.util.HashMap<>();
                            tableData.put("tableName", table.get("name"));
                            // 使用 table 自带的 databaseName，为空则用数据库级别的 name
                            Object tableDbName = table.get("databaseName");
                            tableData.put("databaseName", tableDbName != null ? tableDbName : dbLevelName);
                            tableData.put("tableComment", table.get("comment"));
                            tableData.put("columnCount", table.get("columnCount"));

                            // 提取列信息
                            Object columnsObj = table.get("columns");
                            if (columnsObj instanceof Map) {
                                Map<String, Object> columns = (Map<String, Object>) columnsObj;
                                List<Map<String, Object>> columnList = new java.util.ArrayList<>();

                                for (Map.Entry<String, Object> colEntry : columns.entrySet()) {
                                    Map<String, Object> column = (Map<String, Object>) colEntry.getValue();
                                    Map<String, Object> columnData = new java.util.HashMap<>();
                                    columnData.put("columnName", column.get("name"));
                                    columnData.put("columnType", column.get("type"));
                                    columnData.put("columnComment", column.get("comment"));
                                    columnList.add(columnData);
                                }
                                tableData.put("columns", columnList);
                            }

                            tableList.add(tableData);
                        }
                        flattened.put("tables", tableList);
                        log.info("✅ 展平完成: 提取到 {} 个表信息", tableList.size());
                    } else {
                        log.warn("⚠️ tables不是Map类型或为null: {}", tablesObj);
                    }

                    log.debug("展平后的元数据: {}", flattened);
                    return flattened;
                } else {
                    log.warn("⚠️ databases map为空，无法提取表信息");
                }
            } else {
                log.warn("⚠️ databases不是Map类型或为null: {}", databasesObj);
            }

            log.warn("无法从元数据中提取数据库信息: {}", metadata);
        } catch (Exception e) {
            log.error("❌ 展平元数据失败，原始metadata: {}", metadata, e);
        }

        return flattened;
    }

    /**
     * 展平数据量结构（从嵌套的databases Map中提取所有数据库的数据）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenDataSize(Map<String, Object> dataSize) {
        Map<String, Object> flattened = new java.util.HashMap<>();

        try {
            // 检查是否有databases字段
            Object databasesObj = dataSize.get("databases");
            if (databasesObj instanceof Map) {
                Map<String, Object> databases = (Map<String, Object>) databasesObj;
                log.info("展平数据量信息: 找到 {} 个数据库", databases.size());

                // 获取第一个数据库作为主要数据源
                if (!databases.isEmpty()) {
                    Map.Entry<String, Object> firstEntry = databases.entrySet().iterator().next();
                    Map<String, Object> database = (Map<String, Object>) firstEntry.getValue();

                    // 提取数据量信息
                    flattened.put("databaseType", database.get("type"));
                    flattened.put("databaseName", database.get("name"));
                    flattened.put("storage", database.get("storage"));
                    flattened.put("rowCount", database.get("rowCount"));
                    flattened.put("tableCount", database.get("tableCount"));
                    flattened.put("columnCount", database.get("columnCount"));

                    // 保存所有数据库的原始数据，用于后续处理
                    flattened.put("allDatabases", databases);

                    log.debug("展平后的数据量信息: {}", flattened);
                    return flattened;
                }
            }

            log.warn("无法从数据量信息中提取数据库信息: {}", dataSize);
        } catch (Exception e) {
            log.error("展平数据量信息失败", e);
        }

        return flattened;
    }

    /**
     * 构建数据库URL
     */
    @SuppressWarnings("unchecked")
    private String buildDatabaseUrl(Map<String, Object> database) {
        try {
            String type = (String) database.get("type");
            String host = (String) database.get("host");
            Object portObj = database.get("port");
            String name = (String) database.get("name");

            if (type == null || host == null) {
                return "";
            }

            String port = portObj != null ? portObj.toString() : "5432";

            return String.format("jdbc:%s://%s:%s/%s", type, host, port, name);
        } catch (Exception e) {
            log.error("构建数据库URL失败", e);
            return "";
        }
    }

    /**
     * 处理通用指标数据
     */
    private void handleMetricData(String probeKey, Map<String, Object> data) {
        log.debug("收到通用指标数据: probeKey={}, data={}", probeKey, data);
    }

    /**
     * 更新表级统计数据到 table_info 表
     * 更新：现在处理所有数据库，而不是只处理第一个
     */
    @SuppressWarnings("unchecked")
    private void updateTablePerformanceData(String probeKey, Map<String, Object> dataSize) {
        try {
            Object databasesObj = dataSize.get("databases");
            if (!(databasesObj instanceof Map)) {
                log.warn("databases不是Map类型，无法更新表级统计数据");
                return;
            }

            Map<String, Object> databases = (Map<String, Object>) databasesObj;
            if (databases.isEmpty()) {
                return;
            }

            log.info("========== [updateTablePerformanceData] 开始处理 {} 个数据库的统计数据 ==========", databases.size());

            int totalUpdatedCount = 0;
            int dbIndex = 0;

            // 处理所有数据库
            for (Map.Entry<String, Object> dbEntry : databases.entrySet()) {
                dbIndex++;
                String dbKey = dbEntry.getKey();
                Map<String, Object> database = (Map<String, Object>) dbEntry.getValue();

                log.info("数据库 {}/{}: {}",
                         dbIndex, databases.size(), dbKey);

                // 数据库级别的 name 作为 fallback
                String dbLevelName = (String) database.get("name");

                // 提取表级别的性能数据
                Object tablesObj = database.get("tables");
                if (!(tablesObj instanceof Map)) {
                    log.warn("  tables不是Map类型，跳过此数据库");
                    continue;
                }

                Map<String, Object> tables = (Map<String, Object>) tablesObj;
                log.info("  找到 {} 个表", tables.size());

                int dbUpdatedCount = 0;
                for (Map.Entry<String, Object> entry : tables.entrySet()) {
                    Map<String, Object> table = (Map<String, Object>) entry.getValue();

                    String tableName = (String) table.get("name");
                    // 使用 table 自带的 databaseName，为空则用数据库级别的 name
                    String tableDbName = (String) table.get("databaseName");
                    String databaseName = tableDbName != null ? tableDbName : dbLevelName;
                    Long rowCount = table.get("rowCount") != null ?
                        Long.valueOf(table.get("rowCount").toString()) : null;
                    Long storage = table.get("storage") != null ?
                        Long.valueOf(table.get("storage").toString()) : null;
                    Long indexes = table.get("indexes") != null ?
                        Long.valueOf(table.get("indexes").toString()) : null;

                    log.info("  更新表统计数据: databaseName={}, tableName={}, rowCount={}, storage={}, indexes={}",
                        databaseName, tableName, rowCount, storage, indexes);

                    // 使用 MyBatis-Plus 更新表数据，包含 databaseName
                    LambdaUpdateWrapper<TableInfo> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(TableInfo::getProbeKey, probeKey)
                        .eq(TableInfo::getTableName, tableName)
                        .eq(TableInfo::getDatabaseName, databaseName) // Add databaseName filter
                        .set(rowCount != null, TableInfo::getRowCount, rowCount)
                        .set(storage != null, TableInfo::getDataSize, storage)
                        .set(indexes != null, TableInfo::getIndexSize, indexes); // 添加索引大小更新

                    int updated = tableInfoMapper.update(null, updateWrapper);
                    if (updated > 0) {
                        log.info("  ✓ 表统计数据已更新: {}, rowCount={}, storage={}, indexes={}",
                            tableName, rowCount, storage, indexes);
                        dbUpdatedCount++;
                    } else {
                        // UPDATE失败，尝试INSERT新记录
                        log.info("  → 表未找到，尝试插入新记录: {}", tableName);
                        try {
                            TableInfo newRecord = new TableInfo();
                            newRecord.setProbeKey(probeKey);
                            newRecord.setTableName(tableName);
                            newRecord.setDatabaseName(databaseName);
                            newRecord.setRowCount(rowCount);
                            newRecord.setDataSize(storage);
                            newRecord.setIndexSize(indexes);
                            newRecord.setCreateTime(java.time.LocalDateTime.now());
                            newRecord.setUpdateTime(java.time.LocalDateTime.now());

                            int inserted = tableInfoMapper.insert(newRecord);
                            if (inserted > 0) {
                                log.info("  ✓ 新表记录已插入: {}, rowCount={}, storage={}, indexes={}",
                                        tableName, rowCount, storage, indexes);
                                dbUpdatedCount++;
                            } else {
                                log.warn("  ⚠️ 插入表记录失败: {}", tableName);
                            }
                        } catch (Exception insertEx) {
                            log.error("  ✗ 插入表记录异常: {}", tableName, insertEx);
                        }
                    }

                    // 变更检测：保存快照并检测变化
                    if (changeDetectionService != null && rowCount != null) {
                        try {
                            String maxUpdateTime = table.get("maxUpdateTime") != null
                                    ? table.get("maxUpdateTime").toString() : null;
                            changeDetectionService.saveSnapshotAndDetect(
                                    probeKey, databaseName, tableName,
                                    rowCount, storage, indexes, maxUpdateTime);
                        } catch (Exception e) {
                            log.warn("  变更检测失败（不影响主流程）: table={}, error={}", tableName, e.getMessage());
                        }
                    }

                    // 同步元数据到汇聚表
                    if (aggregationService != null) {
                        try {
                            aggregationService.syncMetadataToAggregation(
                                    probeKey, databaseName, tableName, rowCount, storage, null);
                        } catch (Exception e) {
                            log.debug("  汇聚元数据同步失败: table={}", tableName);
                        }
                    }
                }

                log.info("  数据库 {} 处理完成: 更新了 {} 个表", dbKey, dbUpdatedCount);
                totalUpdatedCount += dbUpdatedCount;
            }

            log.info("✓ 表级统计数据更新完成: 总共更新了 {} 个数据库的 {} 个表",
                     databases.size(), totalUpdatedCount);
            log.info("=======================================================================");

        } catch (Exception e) {
            log.error("更新表级统计数据失败: probeKey={}", probeKey, e);
        }
    }

    @Override
    public String getHandlerName() {
        return "ProbePushMessageHandler";
    }

    /**
     * 自动为数据库中的每个表创建同步任务
     * 当元数据中包含新表时，自动创建 sync_task 以便数据汇聚到后端
     */
    @SuppressWarnings("unchecked")
    private void autoCreateSyncTasks(String probeKey, String databaseName, Map<String, Object> database) {
        Object tablesObj = database.get("tables");
        if (!(tablesObj instanceof Map)) return;

        Map<String, Object> tables = (Map<String, Object>) tablesObj;
        int created = 0;

        for (Map.Entry<String, Object> entry : tables.entrySet()) {
            Map<String, Object> table = (Map<String, Object>) entry.getValue();
            String tableName = (String) table.get("name");
            if (tableName == null) continue;

            // 检查是否已存在对应的同步任务
            Long existing = syncTaskMapper.selectCount(
                new LambdaQueryWrapper<com.lixin.probe.entity.SyncTask>()
                    .eq(com.lixin.probe.entity.SyncTask::getSourceProbeKey, probeKey)
                    .eq(com.lixin.probe.entity.SyncTask::getSourceTableName, tableName));
            if (existing != null && existing > 0) continue;

            // 构建目标配置：汇聚到 Admin 自身的 PostgreSQL
            String targetConfig = buildTargetConfig(databaseName, tableName);

            com.lixin.probe.entity.SyncTask task = com.lixin.probe.entity.SyncTask.builder()
                    .taskName("自动同步-" + databaseName + "." + tableName)
                    .sourceProbeKey(probeKey)
                    .sourceTableName(tableName)
                    .targetType("DATABASE")
                    .targetConfig(targetConfig)
                    .syncMode("INCREMENTAL")
                    .conflictStrategy("UPSERT")
                    .enabled(true)
                    .qualityCheckEnabled(false)
                    .realtimeSyncEnabled(true)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            syncTaskMapper.insert(task);
            created++;
        }

        if (created > 0) {
            log.info("[自动同步] 为 {} 下的 {} 个新表创建了同步任务", databaseName, created);
        }
    }

    /**
     * 构建目标数据库配置（汇聚到 Admin 自身的 PostgreSQL）
     */
    private String buildTargetConfig(String databaseName, String tableName) {
        String jdbcUrl = adminDatasourceUrl;
        // 确保 URL 不包含多余的参数
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            jdbcUrl = "jdbc:postgresql://localhost:5432/probe_db";
        }
        // 目标表放入 aggregated schema，避免与系统表冲突
        String targetTable = "aggregated.\"" + databaseName + "_" + tableName + "\"";

        return String.format(
            "{\"jdbcUrl\":\"%s\",\"username\":\"%s\",\"password\":\"%s\",\"table\":\"%s\",\"dbType\":\"postgresql\"}",
            jdbcUrl, adminDatasourceUsername, adminDatasourcePassword, targetTable);
    }
}
