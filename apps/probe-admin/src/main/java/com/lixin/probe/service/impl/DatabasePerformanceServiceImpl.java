package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.ColumnInfo;
import com.lixin.probe.entity.DatabaseMetadata;
import com.lixin.probe.entity.DatabasePerformance;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.entity.TableInfo;
import com.lixin.probe.mapper.DatabasePerformanceMapper;
import com.lixin.probe.service.DatabaseMetadataService;
import com.lixin.probe.service.DatabasePerformanceService;
import com.lixin.probe.service.ProbeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库性能监控Service实现类
 */
@Service
public class DatabasePerformanceServiceImpl implements DatabasePerformanceService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabasePerformanceServiceImpl.class);

    @Autowired
    private DatabasePerformanceMapper databasePerformanceMapper;

    @Autowired
    @Qualifier("decoratedProbeService")
    private ProbeService probeService;

    @Autowired
    private DatabaseMetadataService databaseMetadataService;

    @Override
    public Page<DatabasePerformance> getPage(int pageNum, int pageSize, String probeKey,
                                             String databaseType, LocalDateTime startTime,
                                             LocalDateTime endTime) {
        Page<DatabasePerformance> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<DatabasePerformance> queryWrapper = new LambdaQueryWrapper<DatabasePerformance>()
            .eq(probeKey != null, DatabasePerformance::getProbeKey, probeKey)
            .eq(databaseType != null, DatabasePerformance::getDatabaseType, databaseType)
            .ge(startTime != null, DatabasePerformance::getCreateTime, startTime)
            .le(endTime != null, DatabasePerformance::getCreateTime, endTime)
            .orderByDesc(DatabasePerformance::getCreateTime);
        return databasePerformanceMapper.selectPage(page, queryWrapper);
    }

    @Override
    public DatabasePerformance getLatestByProbeKey(String probeKey) {
        LambdaQueryWrapper<DatabasePerformance> queryWrapper = new LambdaQueryWrapper<DatabasePerformance>()
            .eq(DatabasePerformance::getProbeKey, probeKey)
            .orderByDesc(DatabasePerformance::getCreateTime);
        return databasePerformanceMapper.selectList(queryWrapper).stream().findFirst().orElse(null);
    }

    @Override
    public List<DatabasePerformance> listByProbeKey(String probeKey, int limit) {
        LambdaQueryWrapper<DatabasePerformance> queryWrapper = new LambdaQueryWrapper<DatabasePerformance>()
            .eq(DatabasePerformance::getProbeKey, probeKey)
            .orderByDesc(DatabasePerformance::getCreateTime)
            .last("LIMIT " + limit);
        return databasePerformanceMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional
    public void save(DatabasePerformance performance) {
        performance.setCreateTime(LocalDateTime.now());
        performance.setUpdateTime(LocalDateTime.now());
        databasePerformanceMapper.insert(performance);
    }

    @Override
    @Transactional
    public void batchSave(List<DatabasePerformance> performanceList) {
        if (performanceList == null || performanceList.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (DatabasePerformance performance : performanceList) {
            performance.setCreateTime(now);
            performance.setUpdateTime(now);
            databasePerformanceMapper.insert(performance);
        }

        log.info("批量保存数据库性能数据: {} 条", performanceList.size());
    }

    @Override
    public Map<String, Object> getStatistics(String probeKey, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> statistics = new HashMap<>();

        List<DatabasePerformance> performances = databasePerformanceMapper.selectList(
            new LambdaQueryWrapper<DatabasePerformance>()
                .eq(DatabasePerformance::getProbeKey, probeKey)
                .ge(startTime != null, DatabasePerformance::getCreateTime, startTime)
                .le(endTime != null, DatabasePerformance::getCreateTime, endTime)
                .orderByAsc(DatabasePerformance::getCreateTime)
        );

        if (performances.isEmpty()) {
            return statistics;
        }

        // 计算统计数据
        double avgConnectionUsage = performances.stream()
            .mapToDouble(p -> p.getConnectionUsage() != null ? p.getConnectionUsage() : 0)
            .average().orElse(0);

        double avgQueryTime = performances.stream()
            .mapToLong(p -> p.getAvgQueryTime() != null ? p.getAvgQueryTime() : 0)
            .average().orElse(0);

        double avgQps = performances.stream()
            .mapToDouble(p -> p.getQps() != null ? p.getQps() : 0)
            .average().orElse(0);

        long totalSlowQueries = performances.stream()
            .mapToLong(p -> p.getSlowQueryCount() != null ? p.getSlowQueryCount() : 0)
            .sum();

        statistics.put("avgConnectionUsage", avgConnectionUsage);
        statistics.put("avgQueryTime", avgQueryTime);
        statistics.put("avgQps", avgQps);
        statistics.put("totalSlowQueries", totalSlowQueries);
        statistics.put("dataPoints", performances.size());

        return statistics;
    }

    @Override
    @Transactional
    public void deleteBefore(LocalDateTime time) {
        databasePerformanceMapper.delete(
            new LambdaQueryWrapper<DatabasePerformance>()
                .lt(DatabasePerformance::getCreateTime, time)
        );
        log.info("删除历史性能数据: 时间之前 {}", time);
    }

    @Override
    @Transactional
    public void saveMetadata(String probeKey, Map<String, Object> metadata) {
        log.info("========== [saveMetadata] 开始保存数据库元数据 ==========");
        log.info("probeKey={}, metadata.keys={}", probeKey, metadata != null ? metadata.keySet() : "null");

        try {
            // 查询探针ID
            log.info("步骤1: 查询探针信息...");
            Probe probe = probeService.getByProbeKey(probeKey);
            if (probe == null) {
                log.error("✗ 探针不存在，无法保存数据库元数据: probeKey={}", probeKey);
                return;
            }
            log.info("✓ 探针存在: id={}, name={}, type={}", probe.getId(), probe.getName(), probe.getType());

            // 构建DatabaseMetadata实体并保存
            log.info("步骤2: 构建DatabaseMetadata实体...");
            String databaseType = (String) metadata.get("databaseType");
            String databaseName = (String) metadata.get("databaseName");
            String version = (String) metadata.get("version");
            String charset = (String) metadata.get("charset");
            String collation = (String) metadata.get("collation");
            String url = (String) metadata.get("url");

            log.info("  - databaseType: {}", databaseType);
            log.info("  - databaseName: {}", databaseName);
            log.info("  - version: {}", version);
            log.info("  - charset: {}", charset);
            log.info("  - collation: {}", collation);
            log.info("  - url: {}", url);

            DatabaseMetadata dbMetadata = DatabaseMetadata.builder()
                    .probeKey(probeKey)
                    .databaseType(databaseType)
                    .databaseName(databaseName)
                    .version(version)
                    .charset(charset)
                    .collation(collation)
                    .url(url)
                    .build();

            log.info("步骤3: 保存DatabaseMetadata到数据库...");
            databaseMetadataService.saveMetadata(dbMetadata);
            log.info("✓ 数据库元数据已保存: databaseName={}", dbMetadata.getDatabaseName());

            // 保存表信息（如果存在）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tables = (List<Map<String, Object>>) metadata.get("tables");
            log.info("步骤4: 检查表信息...");
            log.info("  - tables: {}", tables != null ? tables.size() + "个表" : "null");

            if (tables != null && !tables.isEmpty()) {
                log.info("步骤5: 开始保存表和列信息...");
                List<TableInfo> tableInfos = new java.util.ArrayList<>();
                int tableIndex = 0;

                for (Map<String, Object> tableData : tables) {
                    tableIndex++;
                    String tableName = (String) tableData.get("tableName");
                    String tableDatabaseName = (String) tableData.get("databaseName"); // Extract databaseName for this table
                    log.info("  处理表 {}/{}: databaseName={}, tableName={}", tableIndex, tables.size(), tableDatabaseName, tableName);

                    TableInfo tableInfo = TableInfo.builder()
                            .probeKey(probeKey)
                            .databaseName(tableDatabaseName) // Set databaseName
                            .tableName(tableName)
                            .engine((String) tableData.get("engine"))
                            .rowCount(tableData.get("rowCount") != null ?
                                    Long.valueOf(tableData.get("rowCount").toString()) : null)
                            .dataSize(tableData.get("dataSize") != null ?
                                    Long.valueOf(tableData.get("dataSize").toString()) : null)
                            .indexSize(tableData.get("indexSize") != null ?
                                    Long.valueOf(tableData.get("indexSize").toString()) : null)
                            .totalSize(tableData.get("totalSize") != null ?
                                    Long.valueOf(tableData.get("totalSize").toString()) : null)
                            .createTimeStr((String) tableData.get("createTimeStr"))
                            .updateTimeStr((String) tableData.get("updateTimeStr"))
                            .build();
                    tableInfos.add(tableInfo);

                    // 保存列信息（如果存在）
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> columns = (List<Map<String, Object>>) tableData.get("columns");
                    if (columns != null && !columns.isEmpty()) {
                        log.info("    - 表 {} 包含 {} 个列", tableName, columns.size());
                        List<ColumnInfo> columnInfos = new java.util.ArrayList<>();

                        for (Map<String, Object> columnData : columns) {
                            String columnName = (String) columnData.get("columnName");
                            ColumnInfo columnInfo = ColumnInfo.builder()
                                    .probeKey(probeKey)
                                    .databaseName(tableDatabaseName) // Set databaseName
                                    .tableName(tableName)
                                    .columnName(columnName)
                                    .columnType((String) columnData.get("columnType"))
                                    .dataType((String) columnData.get("dataType"))
                                    .isNullable((Boolean) columnData.get("isNullable"))
                                    .keyType((String) columnData.get("keyType"))
                                    .defaultValue((String) columnData.get("defaultValue"))
                                    .extra((String) columnData.get("extra"))
                                    .comment((String) columnData.get("comment"))
                                    .build();
                            columnInfos.add(columnInfo);
                        }
                        databaseMetadataService.batchSaveColumns(columnInfos);
                        log.info("    ✓ 保存列信息成功: tableName={}, columnCount={}", tableName, columnInfos.size());
                    } else {
                        log.info("    - 表 {} 没有列信息", tableName);
                    }
                }

                databaseMetadataService.batchSaveTables(tableInfos);
                log.info("✓ 保存表信息成功: probeKey={}, tableCount={}", probeKey, tableInfos.size());
            } else {
                log.info("  - 没有表信息需要保存");
            }

            log.info("✓ 元数据保存完成: probeKey={}", probeKey);
            log.info("=======================================================");

        } catch (Exception e) {
            log.error("✗ 保存数据库元数据失败: probeKey={}", probeKey, e);
            log.error("错误详情: {}", e.getMessage(), e);
            throw new RuntimeException("保存数据库元数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void savePerformanceData(String probeKey, Map<String, Object> performanceData) {
        try {
            // 查询探针ID
            Probe probe = probeService.getByProbeKey(probeKey);
            if (probe == null) {
                log.warn("探针不存在，无法保存数据库性能数据: probeKey={}", probeKey);
                return;
            }

            // 构建DatabasePerformance实体
            DatabasePerformance performance = DatabasePerformance.builder()
                    .probeId(probe.getId())
                    .probeKey(probeKey)
                    .databaseType((String) performanceData.get("databaseType"))
                    .connectionUsage(performanceData.get("connectionUsage") != null ?
                        Double.valueOf(performanceData.get("connectionUsage").toString()) : null)
                    .qps(performanceData.get("qps") != null ?
                        Double.valueOf(performanceData.get("qps").toString()) : null)
                    .avgQueryTime(performanceData.get("avgQueryTime") != null ?
                        Long.valueOf(performanceData.get("avgQueryTime").toString()) : null)
                    .slowQueryCount(performanceData.get("slowQueryCount") != null ?
                        Long.valueOf(performanceData.get("slowQueryCount").toString()) : null)
                    .timestamp(System.currentTimeMillis())
                    .createTime(LocalDateTime.now())
                    .build();

            // 保存性能数据
            databasePerformanceMapper.insert(performance);
            log.debug("保存数据库性能数据成功: probeKey={}, qps={}", probeKey, performance.getQps());

        } catch (Exception e) {
            log.error("保存数据库性能数据失败: probeKey={}", probeKey, e);
            throw new RuntimeException("保存数据库性能数据失败: " + e.getMessage(), e);
        }
    }
}
