package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.DatabasePerformance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据库性能监控Service接口
 */
public interface DatabasePerformanceService {

    /**
     * 分页查询性能数据
     */
    Page<DatabasePerformance> getPage(int pageNum, int pageSize, String probeKey,
                                      String databaseType, LocalDateTime startTime,
                                      LocalDateTime endTime);

    /**
     * 根据探针KEY查询最新性能数据
     */
    DatabasePerformance getLatestByProbeKey(String probeKey);

    /**
     * 根据探针KEY查询性能数据列表
     */
    List<DatabasePerformance> listByProbeKey(String probeKey, int limit);

    /**
     * 保存性能数据
     */
    void save(DatabasePerformance performance);

    /**
     * 批量保存性能数据
     */
    void batchSave(List<DatabasePerformance> performanceList);

    /**
     * 获取性能统计信息
     */
    Map<String, Object> getStatistics(String probeKey, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 删除指定时间之前的性能数据
     */
    void deleteBefore(LocalDateTime time);

    /**
     * 保存数据库元数据
     * @param probeKey 探针标识
     * @param metadata 元数据Map
     */
    void saveMetadata(String probeKey, Map<String, Object> metadata);

    /**
     * 保存数据库性能数据
     * @param probeKey 探针标识
     * @param performanceData 性能数据Map
     */
    void savePerformanceData(String probeKey, Map<String, Object> performanceData);
}
