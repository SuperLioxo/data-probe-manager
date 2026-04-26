package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.ColumnInfo;
import com.lixin.probe.entity.DatabaseMetadata;
import com.lixin.probe.entity.TableInfo;
import com.lixin.probe.mapper.ColumnInfoMapper;
import com.lixin.probe.mapper.DatabaseMetadataMapper;
import com.lixin.probe.mapper.TableInfoMapper;
import com.lixin.probe.service.DatabaseMetadataService;
import com.lixin.probe.service.DatabaseProbeService;
import com.lixin.probe.service.ProbeControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库元数据Service实现
 */
@Service
public class DatabaseMetadataServiceImpl implements DatabaseMetadataService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseMetadataServiceImpl.class);

    @Autowired
    private DatabaseMetadataMapper metadataMapper;

    @Autowired
    private TableInfoMapper tableInfoMapper;

    @Autowired
    private ColumnInfoMapper columnInfoMapper;

    @Lazy
    @Autowired
    private DatabaseProbeService databaseProbeService;

    @Lazy
    @Autowired
    private ProbeControlService probeControlService;

    @Override
    public DatabaseMetadata getLatestByProbeKey(String probeKey) {
        // 使用自定义 SQL 避免 PostgreSQL 保留关键字冲突
        DatabaseMetadata metadata = metadataMapper.selectByProbeKey(probeKey);
        if (metadata != null) {
            log.info("查询到元数据: probeKey={}, databaseType={}, version={}, url={}, databaseName={}",
                probeKey, metadata.getDatabaseType(), metadata.getVersion(), metadata.getUrl(), metadata.getDatabaseName());
        } else {
            log.warn("未查询到元数据: probeKey={}", probeKey);
        }

        // DEBUG: 打印所有字段值
        if (metadata != null) {
            log.debug("元数据详情: id={}, probeKey={}, databaseType={}, databaseName={}, version={}, charset={}, collation={}, url={}",
                metadata.getId(), metadata.getProbeKey(), metadata.getDatabaseType(),
                metadata.getDatabaseName(), metadata.getVersion(), metadata.getCharset(),
                metadata.getCollation(), metadata.getUrl());
        }

        return metadata;
    }

    @Override
    public DatabaseMetadata getLatestByProbeKeyAndDatabase(String probeKey, String databaseName) {
        log.info("查询指定数据库的元数据: probeKey={}, databaseName={}", probeKey, databaseName);

        // 使用自定义 SQL 查询指定数据库的元数据
        DatabaseMetadata metadata = metadataMapper.selectByProbeKeyAndDatabase(probeKey, databaseName);

        if (metadata != null) {
            log.info("查询到元数据: probeKey={}, databaseName={}, databaseType={}, version={}",
                probeKey, databaseName, metadata.getDatabaseType(), metadata.getVersion());
        } else {
            log.warn("未查询到指定数据库的元数据: probeKey={}, databaseName={}", probeKey, databaseName);
        }

        return metadata;
    }

    @Override
    @Transactional
    public void saveMetadata(DatabaseMetadata metadata) {
        // ⭐ 修复：使用 probeKey + databaseName 作为联合唯一键
        // 这样统一probeKey架构下，每个数据库实例都有独立的元数据记录
        DatabaseMetadata existing = metadataMapper.selectByProbeKeyAndDatabase(
            metadata.getProbeKey(),
            metadata.getDatabaseName()
        );

        if (existing != null) {
            // 记录已存在，更新
            metadata.setId(existing.getId());
            metadataMapper.updateById(metadata);
            log.debug("更新数据库元数据: probeKey={}, databaseName={}",
                metadata.getProbeKey(), metadata.getDatabaseName());
        } else {
            // 新记录，插入
            metadataMapper.insert(metadata);
            log.debug("插入数据库元数据: probeKey={}, databaseName={}",
                metadata.getProbeKey(), metadata.getDatabaseName());
        }
    }

    @Override
    public Page<TableInfo> getTables(String probeKey, int pageNum, int pageSize, String search) {
        return getTables(probeKey, null, pageNum, pageSize, search);
    }

    @Override
    public Page<TableInfo> getTables(String probeKey, String databaseName, int pageNum, int pageSize, String search) {
        Page<TableInfo> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TableInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TableInfo::getProbeKey, probeKey);

        // Add databaseName filter if provided
        if (StringUtils.hasText(databaseName)) {
            wrapper.eq(TableInfo::getDatabaseName, databaseName);
        }

        if (StringUtils.hasText(search)) {
            wrapper.like(TableInfo::getTableName, search);
        }

        wrapper.orderByDesc(TableInfo::getUpdateTime);
        return tableInfoMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional
    public void saveTable(TableInfo tableInfo) {
        // 检查是否已存在（按 probeKey + tableName + databaseName 查重）
        // 同时兼容 databaseName 为空的历史记录，避免重复插入
        LambdaQueryWrapper<TableInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TableInfo::getProbeKey, tableInfo.getProbeKey())
                .eq(TableInfo::getTableName, tableInfo.getTableName());
        if (StringUtils.hasText(tableInfo.getDatabaseName())) {
            wrapper.and(w -> w.eq(TableInfo::getDatabaseName, tableInfo.getDatabaseName())
                    .or().isNull(TableInfo::getDatabaseName)
                    .or().eq(TableInfo::getDatabaseName, ""));
        }

        TableInfo existing = tableInfoMapper.selectOne(wrapper);
        if (existing != null) {
            tableInfo.setId(existing.getId());
            tableInfoMapper.updateById(tableInfo);
        } else {
            tableInfoMapper.insert(tableInfo);
        }
    }

    @Override
    @Transactional
    public void batchSaveTables(List<TableInfo> tableInfos) {
        for (TableInfo tableInfo : tableInfos) {
            saveTable(tableInfo);
        }
    }

    @Override
    public List<ColumnInfo> getTableStructure(String probeKey, String tableName) {
        return getTableStructure(probeKey, null, tableName);
    }

    @Override
    public List<ColumnInfo> getTableStructure(String probeKey, String databaseName, String tableName) {
        LambdaQueryWrapper<ColumnInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ColumnInfo::getProbeKey, probeKey)
                .eq(ColumnInfo::getTableName, tableName);

        // Add databaseName filter if provided
        if (StringUtils.hasText(databaseName)) {
            wrapper.eq(ColumnInfo::getDatabaseName, databaseName);
        }

        wrapper.orderByAsc(ColumnInfo::getId);
        return columnInfoMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public void saveColumn(ColumnInfo columnInfo) {
        // 检查是否已存在（包括databaseName）
        LambdaQueryWrapper<ColumnInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ColumnInfo::getProbeKey, columnInfo.getProbeKey())
                .eq(ColumnInfo::getTableName, columnInfo.getTableName())
                .eq(ColumnInfo::getColumnName, columnInfo.getColumnName());

        // Add databaseName filter if set
        if (StringUtils.hasText(columnInfo.getDatabaseName())) {
            wrapper.eq(ColumnInfo::getDatabaseName, columnInfo.getDatabaseName());
        }

        ColumnInfo existing = columnInfoMapper.selectOne(wrapper);
        if (existing != null) {
            columnInfo.setId(existing.getId());
            columnInfoMapper.updateById(columnInfo);
        } else {
            columnInfoMapper.insert(columnInfo);
        }
    }

    @Override
    @Transactional
    public void batchSaveColumns(List<ColumnInfo> columnInfos) {
        for (ColumnInfo columnInfo : columnInfos) {
            saveColumn(columnInfo);
        }
    }

    @Override
    public Map<String, Object> getTableStats(String probeKey) {
        Map<String, Object> stats = new HashMap<>();

        // 总表数
        LambdaQueryWrapper<TableInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TableInfo::getProbeKey, probeKey);
        Long totalTables = tableInfoMapper.selectCount(wrapper);
        stats.put("totalTables", totalTables);

        // 总行数和总大小
        List<TableInfo> tables = tableInfoMapper.selectList(wrapper);
        long totalRows = tables.stream().mapToLong(t -> t.getRowCount() != null ? t.getRowCount() : 0).sum();
        long totalSize = tables.stream().mapToLong(t -> t.getTotalSize() != null ? t.getTotalSize() : 0).sum();

        stats.put("totalRows", totalRows);
        stats.put("totalSize", totalSize);

        return stats;
    }

    @Override
    @Transactional
    public void deleteByProbeKey(String probeKey) {
        // 删除元数据
        LambdaQueryWrapper<DatabaseMetadata> metadataWrapper = new LambdaQueryWrapper<>();
        metadataWrapper.eq(DatabaseMetadata::getProbeKey, probeKey);
        metadataMapper.delete(metadataWrapper);

        // 删除表信息
        LambdaQueryWrapper<TableInfo> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper.eq(TableInfo::getProbeKey, probeKey);
        tableInfoMapper.delete(tableWrapper);

        // 删除字段信息
        LambdaQueryWrapper<ColumnInfo> columnWrapper = new LambdaQueryWrapper<>();
        columnWrapper.eq(ColumnInfo::getProbeKey, probeKey);
        columnInfoMapper.delete(columnWrapper);

        log.info("删除探元数据成功: {}", probeKey);
    }

    @Override
    public Map<String, Object> queryTableData(String probeKey, String databaseName, String tableName, int pageNum, int pageSize) {
        log.info("[表数据查询] 收到查询请求: probeKey={}, databaseName={}, tableName={}, pageNum={}, pageSize={}",
                probeKey, databaseName, tableName, pageNum, pageSize);

        try {
            // ⭐ 优先使用传递的 databaseName 参数（前端传递的）
            if (databaseName != null && !databaseName.isEmpty()) {
                log.info("[表数据查询] 使用传递的databaseName: {}", databaseName);
            } else {
                // 如果没有传递，尝试自动获取
                log.info("[表数据查询] 未传递databaseName，尝试自动获取...");

                // 1. 优先从 database_probe 表获取数据库名称
                try {
                    com.lixin.probe.entity.DatabaseProbe databaseProbe = databaseProbeService.getByProbeKey(probeKey);
                    if (databaseProbe != null && databaseProbe.getDatabaseName() != null && !databaseProbe.getDatabaseName().isEmpty()) {
                        databaseName = databaseProbe.getDatabaseName();
                        log.info("[表数据查询] 从 database_probe 表获取数据库名称: probeKey={}, databaseName={}",
                                probeKey, databaseName);
                    }
                } catch (Exception e) {
                    log.debug("[表数据查询] database_probe 表查询失败: {}", e.getMessage());
                }

                // 2. 如果 database_probe 表中没有，尝试从 probeKey 提取
                if (databaseName == null) {
                    databaseName = extractDatabaseNameFromProbeKey(probeKey);
                    if (databaseName != null) {
                        log.info("[表数据查询] 从 probeKey 提取数据库名称: probeKey={}, databaseName={}",
                                probeKey, databaseName);
                    }
                }

                // 3. 如果还是无法确定，尝试查询最新的元数据
                if (databaseName == null) {
                    DatabaseMetadata metadata = getLatestByProbeKey(probeKey);
                    if (metadata != null) {
                        databaseName = metadata.getDatabaseName();
                        log.info("[表数据查询] 从最新元数据获取数据库名称: probeKey={}, databaseName={}",
                                probeKey, databaseName);
                    }
                }

                // 4. 如果所有方法都失败，返回错误
                if (databaseName == null) {
                    log.warn("[表数据查询] 无法确定databaseName: probeKey={}", probeKey);
                    Map<String, Object> result = new HashMap<>();
                    result.put("error", "无法确定数据库名称，请选择数据库实例");
                    result.put("columns", List.of());
                    result.put("rows", List.of());
                    result.put("total", 0);
                    result.put("pageNum", pageNum);
                    result.put("pageSize", pageSize);
                    return result;
                }
            }

            log.info("[表数据查询] 最终确定databaseName: {}", databaseName);

            // 通过ProbeControlService发送WebSocket查询请求
            return probeControlService.queryTableData(probeKey, databaseName, tableName, pageNum, pageSize);

        } catch (Exception e) {
            log.error("[表数据查询] 查询异常: probeKey={}, tableName={}", probeKey, tableName, e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", "查询失败: " + e.getMessage());
            result.put("columns", List.of());
            result.put("rows", List.of());
            result.put("total", 0);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);
            return result;
        }
    }

    /**
     * 从probeKey中提取databaseName
     * 例如: AGENT-database-test_db_2-xxx → test_db_2
     */
    private String extractDatabaseNameFromProbeKey(String probeKey) {
        if (probeKey == null) {
            return null;
        }

        String[] parts = probeKey.split("-");
        if (parts.length >= 4 && "database".equalsIgnoreCase(parts[1])) {
            // 格式: AGENT-database-{databaseName}-{random}
            return parts[2];
        }

        return null;
    }

    @Autowired
    private javax.sql.DataSource dataSource;
}
