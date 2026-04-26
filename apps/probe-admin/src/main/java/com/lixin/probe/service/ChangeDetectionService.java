package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.ChangeLog;
import com.lixin.probe.entity.DataSnapshot;

import java.util.List;
import java.util.Map;

public interface ChangeDetectionService {

    /**
     * 保存快照并检测变化（L1级别：基于row_count和data_size对比）
     */
    List<ChangeLog> saveSnapshotAndDetect(String probeKey, String databaseName,
                                          String tableName, Long rowCount,
                                          Long dataSize, Long indexSize,
                                          String maxUpdateTime);

    /**
     * 获取表的最新两次快照
     */
    List<DataSnapshot> getLatestSnapshots(String probeKey, String tableName, int limit);

    /**
     * 分页查询变化日志
     */
    Page<ChangeLog> getChangeLogPage(String probeKey, String tableName,
                                       String changeType, int pageNum, int pageSize);

    /**
     * 获取变化统计
     */
    Map<String, Object> getChangeStatistics(String probeKey);

    /**
     * 获取指定探针的所有变化日志
     */
    List<ChangeLog> getRecentChanges(String probeKey, int limit);

    /**
     * 基于已有快照重新检测变化
     */
    List<ChangeLog> redetectFromLatestSnapshots(String probeKey);

    /**
     * 处理Agent上报的CDC事件（行级变更）
     */
    void processCDCEvents(String agentCode, String eventsJson);

    /**
     * 处理Agent上报的数据源心跳
     */
    void processDatasourceHeartbeat(String agentCode, String heartbeatJson);
}
