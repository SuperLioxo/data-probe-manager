package com.lixin.probe.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.service.AggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据汇聚服务实现类
 * <p>
 * 管理独立的 aggregation schema，将各探测数据源（MySQL、PostgreSQL）的元数据与数据
 * 统一汇聚到本系统的 PostgreSQL 数据库中，前端从此汇聚库读取数据。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>数据源注册：将探测到的数据源信息写入 aggregation.data_source_registry 表</li>
 *   <li>元数据同步：将表级元数据（表名、行数、列数等）写入 aggregation.table_metadata，
 *       并自动关联到连接同一物理数据库的其他数据源</li>
 *   <li>数据查询：根据数据源配置动态构建 JDBC 连接，从源数据库中分页查询实际业务数据</li>
 *   <li>质量记录：记录数据质量检测中的不合格记录</li>
 *   <li>文件注册：将上传的文件元信息记录到 aggregation.file_registry</li>
 * </ul>
 */
@Slf4j
@Service
public class AggregationServiceImpl implements AggregationService {

    /** 系统主数据库的 JdbcTemplate，用于操作 aggregation schema 下的各张表 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** PostgreSQL 数据库默认密码，用于动态构建 PostgreSQL 源数据库连接 */
    @Value("${spring.datasource.password:}")
    private String defaultDbPassword;

    /** PostgreSQL 数据库默认用户名，用于动态构建 PostgreSQL 源数据库连接 */
    @Value("${spring.datasource.username:probe_user}")
    private String defaultDbUsername;

    /**
     * 注册数据源到汇聚库
     * <p>
     * 将数据源的连接信息（主机、端口、数据库名、数据库类型等）写入 aggregation.data_source_registry 表。
     * 如果该 source_id 已存在，则更新其名称、类型、连接信息等字段（UPSERT 语义）。
     *
     * @param sourceId     数据源唯一标识，通常由探测端生成
     * @param sourceName   数据源显示名称
     * @param sourceType   数据源类型（如 mysql、postgresql 等）
     * @param databaseType 数据库类型，用于决定 JDBC 驱动和连接串格式
     * @param host         数据库主机地址
     * @param port         数据库端口号
     * @param databaseName 数据库名称
     * @param agentCode    探针代理编码（当前未写入注册表，预留扩展）
     */
    @Override
    public void registerDataSource(String sourceId, String sourceName, String sourceType,
                                   String databaseType, String host, Integer port,
                                   String databaseName, String agentCode) {
        // UPSERT 语句：若 source_id 冲突则更新已有记录，保证重复注册不会产生重复行
        String sql = """
            INSERT INTO aggregation.data_source_registry (source_id, source_name, source_type, database_type, host, port, database_name, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (source_id) DO UPDATE SET
                source_name = EXCLUDED.source_name,
                source_type = EXCLUDED.source_type,
                database_type = EXCLUDED.database_type,
                host = EXCLUDED.host,
                port = EXCLUDED.port,
                database_name = EXCLUDED.database_name,
                updated_at = NOW()
            """;
        try {
            jdbcTemplate.update(sql, sourceName, sourceName, sourceType, databaseType,
                    host, port, databaseName);
            log.info("[汇聚] 注册数据源: name={}", sourceName);
        } catch (Exception e) {
            log.error("[汇聚] 注册数据源失败: {}", e.getMessage());
        }
    }

    /**
     * 同步单张表的元数据到汇聚库
     * <p>
     * 将表名、行数、列数、所属 schema 写入 aggregation.table_metadata，
     * 并触发 {@link #propagateMetadataToSameDatabase} 将元数据自动关联到
     * 连接同一物理数据库的其他数据源。
     *
     * @param sourceId     数据源唯一标识
     * @param databaseName 数据库名 / schema 名
     * @param tableName    表名
     * @param rowCount     表的行数（可为 null 表示未知）
     * @param dataSize     表的数据大小（当前未使用，预留）
     * @param columns      列信息列表（仅用于统计列数）
     */
    @Override
    public void syncMetadataToAggregation(String sourceId, String databaseName, String tableName,
                                          Long rowCount, Long dataSize, List<Map<String, Object>> columns) {
        // ON CONFLICT DO NOTHING：若该 source_id + table_name 组合已存在则跳过，避免重复插入
        String tableSql = """
            INSERT INTO aggregation.table_metadata (source_id, table_name, row_count, column_count, schema_name, updated_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            ON CONFLICT DO NOTHING
            """;
        try {
            Integer columnCount = columns != null ? columns.size() : null;
            jdbcTemplate.update(tableSql, sourceId, tableName, rowCount, columnCount, databaseName);
            log.debug("[汇聚] 同步表元数据: source={}, table={}", sourceId, tableName);

            // 自动关联：将元数据同步到连接同一数据库的其他数据源
            propagateMetadataToSameDatabase(sourceId, tableName, rowCount, columnCount, databaseName);
        } catch (Exception e) {
            log.error("[汇聚] 同步表元数据失败: {}", e.getMessage());
        }
    }

    /**
     * 从源数据库查询汇聚的实际业务数据（分页）
     * <p>
     * 根据数据源注册信息动态构建 JDBC 连接，直接连接源数据库执行
     * SELECT 查询并返回分页结果。查询完成后关闭动态创建的数据源连接。
     *
     * @param sourceId  数据源唯一标识
     * @param tableName 要查询的表名
     * @param pageNum   页码（从 1 开始）
     * @param pageSize  每页记录数
     * @return 包含 total（总行数）、records（当前页数据）、pageNum、pageSize 的结果 Map；
     *         查询失败时返回 total=0、records 为空列表
     */
    @Override
    public Map<String, Object> queryAggregatedData(String sourceId, String tableName, int pageNum, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 计算分页偏移量：第 1 页 offset=0，第 2 页 offset=pageSize，以此类推
        int offset = (pageNum - 1) * pageSize;
        try {
            // 从汇聚库查询数据源的连接信息
            Map<String, Object> sourceInfo = getDataSourceInfo(sourceId);
            if (sourceInfo == null) {
                log.warn("[汇聚] 数据源不存在: {}", sourceId);
                result.put("total", 0);
                result.put("records", Collections.emptyList());
                return result;
            }

            String databaseType = (String) sourceInfo.get("database_type");
            String schemaName = (String) sourceInfo.get("database_name");
            // 根据数据库类型动态构建 JdbcTemplate（MySQL 或 PostgreSQL）
            JdbcTemplate sourceJdbc = buildSourceJdbcTemplate(sourceInfo);

            String qualifiedTable = sanitize(tableName);
            // 对于 PostgreSQL，使用 schema.table 格式如果指定了 schema
            if ("postgresql".equalsIgnoreCase(databaseType) && schemaName != null) {
                // 检查表是否在目标数据库的 public schema 中
                qualifiedTable = sanitize(tableName);
            }

            // 先查询总行数用于分页计算
            String countSql = "SELECT COUNT(*) FROM " + qualifiedTable;
            Long total = sourceJdbc.queryForObject(countSql, Long.class);

            // 使用 LIMIT + OFFSET 实现分页查询
            String dataSql = "SELECT * FROM " + qualifiedTable + " LIMIT ? OFFSET ?";
            List<Map<String, Object>> rows = sourceJdbc.queryForList(dataSql, pageSize, offset);

            result.put("total", total);
            result.put("records", rows);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);

            // 清理动态数据源连接，避免连接泄漏
            closeDataSource(sourceJdbc);
        } catch (Exception e) {
            log.warn("[汇聚] 查询汇聚数据失败: sourceId={}, table={}, error={}", sourceId, tableName, e.getMessage(), e);
            result.put("total", 0);
            result.put("records", Collections.emptyList());
        }
        return result;
    }

    /**
     * 获取汇聚库的整体统计数据
     * <p>
     * 分别统计 aggregation schema 下四张核心表的记录数：
     * 数据源数量、表元数据数量、文件数量、不合格记录数量。
     *
     * @return 包含 dataSourceCount、tableCount、fileCount、badRecordCount 的统计 Map
     */
    @Override
    public Map<String, Object> getAggregationStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            Long sourceCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM aggregation.data_source_registry", Long.class);
            Long tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM aggregation.table_metadata", Long.class);
            Long fileCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM aggregation.file_registry", Long.class);
            Long badRecordCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM aggregation.quality_bad_records", Long.class);
            stats.put("dataSourceCount", sourceCount);
            stats.put("tableCount", tableCount);
            stats.put("fileCount", fileCount);
            stats.put("badRecordCount", badRecordCount);
        } catch (Exception e) {
            log.warn("[汇聚] 获取汇聚统计失败: {}", e.getMessage());
            stats.put("dataSourceCount", 0);
            stats.put("tableCount", 0);
            stats.put("fileCount", 0);
            stats.put("badRecordCount", 0);
        }
        return stats;
    }

    /**
     * 记录一条数据质量不合格记录
     * <p>
     * 将不合格的行数据、违反的质量规则列表及原因写入 aggregation.quality_bad_records 表，
     * 行数据和规则列表以 JSONB 格式存储，便于后续查询和分析。
     *
     * @param syncTaskId    同步任务 ID
     * @param sourceId      数据源 ID
     * @param tableName     表名
     * @param rowData       不合格行的完整数据
     * @param violatedRules 违反的规则名称列表
     * @param reason        不合格原因描述
     */
    @Override
    public void recordBadRecord(Long syncTaskId, Long sourceId, String tableName,
                                Map<String, Object> rowData, List<String> violatedRules, String reason) {
        // rowData 和 violated_rules 使用 ?::jsonb 语法将 JSON 字符串转为 PostgreSQL JSONB 类型
        String sql = """
            INSERT INTO aggregation.quality_bad_records (sync_task_id, source_id, table_name, row_data, violated_rules, rejection_reason, detected_at)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, NOW())
            """;
        try {
            jdbcTemplate.update(sql, syncTaskId, sourceId, tableName,
                    JSON.toJSONString(rowData), JSON.toJSONString(violatedRules), reason);
        } catch (Exception e) {
            log.error("[汇聚] 记录不合格数据失败: {}", e.getMessage());
        }
    }

    /**
     * 注册文件到汇聚库
     * <p>
     * 将上传文件的基本信息（名称、路径、大小、MD5 等）写入 aggregation.file_registry 表，
     * 用于文件追踪和管理。
     *
     * @param sourceId         所属数据源 ID
     * @param fileName         原始文件名
     * @param filePath         文件路径
     * @param fileSize         文件大小（字节）
     * @param fileExtension    文件扩展名
     * @param fileMd5          文件 MD5 校验值
     * @param storagePath      实际存储路径
     * @param aggregationTable 该文件数据对应的汇聚表名
     * @param uploadedBy       上传人
     */
    @Override
    public void registerFile(Long sourceId, String fileName, String filePath, Long fileSize,
                             String fileExtension, String fileMd5, String storagePath,
                             String aggregationTable, String uploadedBy) {
        String sql = """
            INSERT INTO aggregation.file_registry (source_id, file_name, file_path, file_size, file_extension, file_md5, storage_path, aggregation_table, uploaded_by, uploaded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """;
        try {
            jdbcTemplate.update(sql, sourceId, fileName, filePath, fileSize,
                    fileExtension, fileMd5, storagePath, aggregationTable, uploadedBy);
        } catch (Exception e) {
            log.error("[汇聚] 注册文件失败: {}", e.getMessage());
        }
    }

    /**
     * 获取所有已注册的数据源列表
     * <p>
     * 从 aggregation.data_source_registry 表查询全部数据源，按注册时间倒序排列。
     *
     * @return 数据源列表，每项为一个包含数据源全部字段的 Map；查询失败时返回空列表
     */
    @Override
    public List<Map<String, Object>> getAggregatedDataSources() {
        try {
            return jdbcTemplate.queryForList("SELECT * FROM aggregation.data_source_registry ORDER BY registered_at DESC");
        } catch (Exception e) {
            log.warn("[汇聚] 获取数据源列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 分页查询汇聚库中的表元数据列表
     * <p>
     * 支持按数据源 ID 过滤和按表名关键字模糊搜索。
     * 通过 LEFT JOIN data_source_registry 补全 schema_name 字段：
     * 如果 table_metadata 中的 schema_name 为空，则使用 data_source_registry 中的 database_name。
     * <p>
     * 当首次查询某数据源的表列表且结果为空时，会触发懒加载机制
     * {@link #ensureMetadataPropagated}，尝试从同库的其他数据源复制元数据。
     *
     * @param sourceId 数据源 ID（可选，为 null 时不过滤）
     * @param keyword  表名关键字（可选，为 null 时不过滤）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页记录数（上限 500）
     * @return 包含 records（当前页数据）、total（总数）、pageNum、pageSize 的分页结果 Map
     */
    @Override
    public Map<String, Object> getAggregatedTables(String sourceId, String keyword, int pageNum, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 防御性分页参数处理：pageNum 最小为 1，pageSize 在 1~500 之间
            int offset = (Math.max(pageNum, 1) - 1) * Math.max(pageSize, 1);
            int limit = Math.min(Math.max(pageSize, 1), 500);

            // 动态构建 WHERE 子句，根据传入的过滤条件拼接 SQL
            StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
            List<Object> conditions = new java.util.ArrayList<>();

            if (sourceId != null && !sourceId.isEmpty()) {
                whereClause.append(" AND t.source_id = ?");
                conditions.add(sourceId);
            }
            if (keyword != null && !keyword.isEmpty()) {
                whereClause.append(" AND LOWER(t.table_name) LIKE ?");
                conditions.add("%" + keyword.toLowerCase() + "%");
            }

            // 先查询符合条件的总记录数
            String countSql = "SELECT COUNT(*) FROM aggregation.table_metadata t" + whereClause;
            Long total = jdbcTemplate.queryForObject(countSql, Long.class, conditions.toArray());

            // 核心查询：LEFT JOIN data_source_registry 以补全 schema_name
            // COALESCE(NULLIF(t.schema_name, ''), r.database_name) 的逻辑：
            //   若 table_metadata 中的 schema_name 非空则直接使用，
            //   否则回退到 data_source_registry 中的 database_name
            String dataSql = "SELECT t.source_id, t.table_name, t.table_comment, t.row_count, t.column_count, "
                    + "COALESCE(NULLIF(t.schema_name, ''), r.database_name) AS schema_name "
                    + "FROM aggregation.table_metadata t "
                    + "LEFT JOIN aggregation.data_source_registry r ON t.source_id = r.source_id"
                    + whereClause
                    + " ORDER BY t.table_name LIMIT ? OFFSET ?";
            List<Object> dataArgs = new java.util.ArrayList<>(conditions);
            dataArgs.add(limit);
            dataArgs.add(offset);

            List<Map<String, Object>> tables = jdbcTemplate.queryForList(dataSql, dataArgs.toArray());

            // 懒加载机制：如果指定了 sourceId 且第一页查询结果为空，
            // 说明该数据源可能尚未同步元数据，尝试从同库的其他数据源复制
            if (sourceId != null && tables.isEmpty() && offset == 0) {
                ensureMetadataPropagated(sourceId);
                // 重新查询总数和数据
                total = jdbcTemplate.queryForObject(countSql, Long.class, conditions.toArray());
                tables = jdbcTemplate.queryForList(dataSql, dataArgs.toArray());
            }

            result.put("records", tables);
            result.put("total", total != null ? total : 0);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);
        } catch (Exception e) {
            log.warn("[汇聚] 获取表列表失败: {}", e.getMessage());
            result.put("records", Collections.emptyList());
            result.put("total", 0);
        }
        return result;
    }

    /**
     * 确保指定数据源拥有元数据（懒加载传播）
     * <p>
     * 当首次查询某数据源的表列表且结果为空时触发。通过 JOIN data_source_registry
     * 找到与该数据源共享同一物理数据库（host + port + database_name 相同）的其他数据源，
     * 将其已有的 table_metadata 记录复制一份给当前数据源。
     * <p>
     * 使用 NOT EXISTS 子查询避免重复插入已存在的表元数据。
     *
     * @param sourceId 需要补充元数据的数据源 ID
     */
    private void ensureMetadataPropagated(String sourceId) {
        try {
            String sourceName = getSourceName(sourceId);
            // 从同库数据源复制元数据：
            // src 代表"源数据源"（已有元数据），dst 代表"目标数据源"（当前 sourceId）
            // 通过 src.host = dst.host AND src.port = dst.port AND src.database_name = dst.database_name
            // 判定两个数据源连接的是同一个物理数据库
            String copySql = "INSERT INTO aggregation.table_metadata (source_id, table_name, table_comment, row_count, column_count, schema_name, registered_at, updated_at) " +
                    "SELECT ?, t.table_name, ?, t.row_count, t.column_count, t.schema_name, NOW(), NOW() " +
                    "FROM aggregation.table_metadata t " +
                    "JOIN aggregation.data_source_registry src ON src.source_id = t.source_id " +
                    "JOIN aggregation.data_source_registry dst ON dst.source_id = ? " +
                    "WHERE t.source_id != ? " +
                    "AND src.host = dst.host AND src.port = dst.port AND src.database_name = dst.database_name " +
                    "AND NOT EXISTS (SELECT 1 FROM aggregation.table_metadata m WHERE m.source_id = ? AND m.table_name = t.table_name)";
            int rows = jdbcTemplate.update(copySql, sourceId, sourceName, sourceId, sourceId, sourceId);
            if (rows > 0) {
                log.info("[汇聚] 自动关联: 为 {} 复制了 {} 张表的元数据", sourceId, rows);
            }
        } catch (Exception e) {
            log.warn("[汇聚] 自动关联元数据失败: {}", e.getMessage());
        }
    }

    /**
     * 根据 sourceId 查询数据源的显示名称
     *
     * @param sourceId 数据源 ID
     * @return 数据源名称，查询失败时返回空字符串
     */
    private String getSourceName(String sourceId) {
        try {
            List<Map<String, Object>> sources = jdbcTemplate.queryForList(
                    "SELECT source_name FROM aggregation.data_source_registry WHERE source_id = ?", sourceId);
            return sources.isEmpty() ? "" : (String) sources.get(0).get("source_name");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 分页查询数据质量不合格记录
     * <p>
     * 从 aggregation.quality_bad_records 表中查询，支持按同步任务 ID 和表名过滤，
     * 结果按检测时间倒序排列。
     *
     * @param syncTaskId 同步任务 ID（可选，为 null 时不过滤）
     * @param tableName  表名（可选，为 null 或空时不过滤）
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页记录数
     * @return 不合格记录列表，每项包含行数据、违反规则、原因等字段；查询失败时返回空列表
     */
    @Override
    public List<Map<String, Object>> getBadRecords(Long syncTaskId, String tableName, int pageNum, int pageSize) {
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM aggregation.quality_bad_records WHERE 1=1");
            List<Object> params = new ArrayList<>();
            if (syncTaskId != null) {
                sql.append(" AND sync_task_id = ?");
                params.add(syncTaskId);
            }
            if (tableName != null && !tableName.isEmpty()) {
                sql.append(" AND table_name = ?");
                params.add(tableName);
            }
            sql.append(" ORDER BY detected_at DESC LIMIT ? OFFSET ?");
            params.add(pageSize);
            params.add((pageNum - 1) * pageSize);
            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (Exception e) {
            log.warn("[汇聚] 获取不合格记录失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 自动关联元数据到同库数据源
     * <p>
     * 当某个数据源同步了一张表的元数据后，查找所有连接同一物理数据库
     * （host + port + database_name 相同）的其他数据源，将同样的表元数据也写入它们的记录。
     * <p>
     * 使用 NOT EXISTS 子查询确保不会为同一数据源重复插入相同的表元数据。
     *
     * @param sourceId    触发同步的原始数据源 ID
     * @param tableName   表名
     * @param rowCount    行数
     * @param columnCount 列数
     * @param schemaName  数据库 / schema 名称
     */
    private void propagateMetadataToSameDatabase(String sourceId, String tableName,
                                                  Long rowCount, Integer columnCount, String schemaName) {
        try {
            // 核心逻辑：
            // 1. JOIN data_source_registry src 获取触发同步的原始数据源连接信息
            // 2. 在 data_source_registry 中查找与原始数据源同 host/port/database_name 的其他数据源 r
            // 3. 排除原始数据源自身（r.source_id != ?）
            // 4. NOT EXISTS 确保目标数据源不存在该表的元数据，避免重复
            String sql = """
                INSERT INTO aggregation.table_metadata (source_id, table_name, row_count, column_count, schema_name, registered_at, updated_at)
                SELECT r.source_id, ?, ?, ?, ?, NOW(), NOW()
                FROM aggregation.data_source_registry r
                JOIN aggregation.data_source_registry src ON src.source_id = ?
                WHERE r.source_id != ?
                  AND r.host = src.host
                  AND r.port = src.port
                  AND r.database_name = src.database_name
                  AND NOT EXISTS (
                      SELECT 1 FROM aggregation.table_metadata t
                      WHERE t.source_id = r.source_id AND t.table_name = ?
                  )
                """;
            int rows = jdbcTemplate.update(sql, tableName, rowCount, columnCount, schemaName,
                    sourceId, sourceId, tableName);
            if (rows > 0) {
                log.info("[汇聚] 自动关联表元数据到{}个同库数据源: table={}", rows, tableName);
            }
        } catch (Exception e) {
            log.warn("[汇聚] 自动关联表元数据失败: {}", e.getMessage());
        }
    }

    /**
     * SQL 标识符清洗工具方法
     * <p>
     * 移除表名中除字母、数字、下划线以外的所有字符，防止 SQL 注入。
     *
     * @param identifier 待清洗的标识符（如表名）
     * @return 清洗后的安全标识符；输入为 null 时返回空字符串
     */
    private String sanitize(String identifier) {
        if (identifier == null) return "";
        return identifier.replaceAll("[^a-zA-Z0-9_]", "");
    }

    /**
     * 根据 sourceId 查询数据源的完整连接信息
     *
     * @param sourceId 数据源 ID
     * @return 包含 host、port、database_name、database_type 等字段的 Map；
     *         不存在或查询失败时返回 null
     */
    private Map<String, Object> getDataSourceInfo(String sourceId) {
        try {
            List<Map<String, Object>> sources = jdbcTemplate.queryForList(
                    "SELECT * FROM aggregation.data_source_registry WHERE source_id = ?", sourceId);
            return sources.isEmpty() ? null : sources.get(0);
        } catch (Exception e) {
            log.error("[汇聚] 查询数据源信息失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据数据源信息动态构建 JdbcTemplate
     * <p>
     * 根据数据库类型（database_type）选择对应的 JDBC 驱动和连接串格式：
     * <ul>
     *   <li>MySQL：使用 com.mysql.cj.jdbc.Driver，强制 IPv4 避免 localhost 的 IPv6 解析问题，
     *       默认用户名/密码为 root/root</li>
     *   <li>PostgreSQL（默认）：使用 org.postgresql.Driver，用户名和密码从配置文件读取</li>
     * </ul>
     *
     * @param sourceInfo 数据源信息 Map，需包含 database_type、host、port、database_name 字段
     * @return 可用的 JdbcTemplate 实例
     */
    private JdbcTemplate buildSourceJdbcTemplate(Map<String, Object> sourceInfo) {
        String databaseType = (String) sourceInfo.get("database_type");
        String host = (String) sourceInfo.get("host");
        Integer port = sourceInfo.get("port") != null ? ((Number) sourceInfo.get("port")).intValue() : 5432;
        String databaseName = (String) sourceInfo.get("database_name");

        String jdbcUrl;
        String driverClassName;
        String username;
        String password;

        if ("mysql".equalsIgnoreCase(databaseType)) {
            driverClassName = "com.mysql.cj.jdbc.Driver";
            // 强制使用 IPv4 地址，避免 localhost 被 Java 解析为 IPv6
            String mysqlHost = "localhost".equals(host) || "127.0.0.1".equals(host) ? "127.0.0.1" : host;
            jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true", mysqlHost, port, databaseName);
            username = "root";
            password = "root";
        } else {
            // 默认作为 PostgreSQL 处理
            driverClassName = "org.postgresql.Driver";
            jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
            username = defaultDbUsername;
            password = defaultDbPassword;
        }

        DriverManagerDataSource ds = new DriverManagerDataSource(jdbcUrl, username, password);
        ds.setDriverClassName(driverClassName);
        return new JdbcTemplate(ds);
    }

    /**
     * 关闭动态创建的数据源连接
     * <p>
     * 当前使用 DriverManagerDataSource，其内部不维护连接池，无需显式关闭。
     * 预留此方法以便后续切换为连接池实现时进行资源释放。
     *
     * @param jdbc 需要关闭的 JdbcTemplate
     */
    private void closeDataSource(JdbcTemplate jdbc) {
        try {
            if (jdbc.getDataSource() instanceof DriverManagerDataSource ds) {
                // DriverManagerDataSource 无需显式关闭
            }
        } catch (Exception ignored) {
        }
    }
}
