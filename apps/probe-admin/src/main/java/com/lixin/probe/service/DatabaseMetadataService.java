package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.ColumnInfo;
import com.lixin.probe.entity.DatabaseMetadata;
import com.lixin.probe.entity.TableInfo;

import java.util.List;
import java.util.Map;

/**
 * 数据库元数据Service接口
 */
public interface DatabaseMetadataService {

    /**
     * 获取最新的数据库元数据
     *
     * @param probeKey 探针KEY
     * @return 数据库元数据
     */
    DatabaseMetadata getLatestByProbeKey(String probeKey);

    /**
     * 获取指定数据库的最新元数据
     *
     * @param probeKey 探针KEY
     * @param databaseName 数据库名称
     * @return 数据库元数据
     */
    DatabaseMetadata getLatestByProbeKeyAndDatabase(String probeKey, String databaseName);

    /**
     * 保存数据库元数据
     *
     * @param metadata 元数据
     */
    void saveMetadata(DatabaseMetadata metadata);

    /**
     * 获取表列表（分页）
     *
     * @param probeKey 探针KEY
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param search 搜索关键词
     * @return 表列表
     */
    Page<TableInfo> getTables(String probeKey, int pageNum, int pageSize, String search);

    /**
     * 获取表列表（分页，支持数据库名称过滤）
     *
     * @param probeKey 探针KEY
     * @param databaseName 数据库名称（可选）
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param search 搜索关键词
     * @return 表列表
     */
    Page<TableInfo> getTables(String probeKey, String databaseName, int pageNum, int pageSize, String search);

    /**
     * 保存表信息
     *
     * @param tableInfo 表信息
     */
    void saveTable(TableInfo tableInfo);

    /**
     * 批量保存表信息
     *
     * @param tableInfos 表信息列表
     */
    void batchSaveTables(List<TableInfo> tableInfos);

    /**
     * 获取表结构
     *
     * @param probeKey 探针KEY
     * @param tableName 表名
     * @return 字段列表
     */
    List<ColumnInfo> getTableStructure(String probeKey, String tableName);

    /**
     * 获取表结构（支持数据库名称过滤）
     *
     * @param probeKey 探针KEY
     * @param databaseName 数据库名称（可选）
     * @param tableName 表名
     * @return 字段列表
     */
    List<ColumnInfo> getTableStructure(String probeKey, String databaseName, String tableName);

    /**
     * 保存字段信息
     *
     * @param columnInfo 字段信息
     */
    void saveColumn(ColumnInfo columnInfo);

    /**
     * 批量保存字段信息
     *
     * @param columnInfos 字段信息列表
     */
    void batchSaveColumns(List<ColumnInfo> columnInfos);

    /**
     * 获取表统计信息
     *
     * @param probeKey 探针KEY
     * @return 统计信息
     */
    Map<String, Object> getTableStats(String probeKey);

    /**
     * 删除指定探针的所有元数据
     *
     * @param probeKey 探针KEY
     */
    void deleteByProbeKey(String probeKey);

    /**
     * 查询表数据（分页）
     *
     * @param probeKey 探针KEY
     * @param databaseName 数据库名称（可选，用于统一probeKey架构）
     * @param tableName 表名
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 查询结果
     */
    Map<String, Object> queryTableData(String probeKey, String databaseName, String tableName, int pageNum, int pageSize);
}
