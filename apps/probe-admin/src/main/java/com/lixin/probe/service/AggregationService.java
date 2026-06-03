package com.lixin.probe.service;

import java.util.List;
import java.util.Map;

/**
 * 汇聚数据库服务
 * 管理独立的 aggregation schema，前端仅从汇聚库读取数据
 */
public interface AggregationService {

    /**
     * 注册数据源到汇聚库
     */
    void registerDataSource(String sourceId, String sourceName, String sourceType,
                            String databaseType, String host, Integer port,
                            String databaseName, String agentCode);

    /**
     * 同步元数据到汇聚库
     */
    void syncMetadataToAggregation(String sourceId, String databaseName, String tableName,
                                   Long rowCount, Long dataSize, List<Map<String, Object>> columns);

    /**
     * 从汇聚库查询数据（动态表）
     */
    Map<String, Object> queryAggregatedData(String sourceId, String tableName, int pageNum, int pageSize);

    /**
     * 获取汇聚库统计
     */
    Map<String, Object> getAggregationStats();

    /**
     * 记录质量不合格数据
     */
    void recordBadRecord(Long syncTaskId, Long sourceId, String tableName,
                         Map<String, Object> rowData, List<String> violatedRules, String reason);

    /**
     * 注册文件到汇聚库
     */
    void registerFile(Long sourceId, String fileName, String filePath, Long fileSize,
                      String fileExtension, String fileMd5, String storagePath,
                      String aggregationTable, String uploadedBy);

    /**
     * 获取已汇聚的数据源列表
     */
    List<Map<String, Object>> getAggregatedDataSources();

    /**
     * 获取已汇聚的表列表（分页）
     */
    Map<String, Object> getAggregatedTables(String sourceId, String keyword, int pageNum, int pageSize);

    /**
     * 查询不合格记录
     */
    List<Map<String, Object>> getBadRecords(Long syncTaskId, String tableName, int pageNum, int pageSize);
}
