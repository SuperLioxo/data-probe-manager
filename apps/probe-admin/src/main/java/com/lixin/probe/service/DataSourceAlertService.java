package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.DataSourceAlertConfig;
import com.lixin.probe.entity.DataSourceAlertRecord;

import java.util.List;
import java.util.Map;

public interface DataSourceAlertService {

    Page<DataSourceAlertConfig> getAlertConfigs(String probeKey, int pageNum, int pageSize);

    DataSourceAlertConfig createAlertConfig(DataSourceAlertConfig config);

    void updateAlertConfig(DataSourceAlertConfig config);

    void deleteAlertConfig(Long id);

    void processHeartbeat(String agentCode, List<Map<String, Object>> heartbeatData);

    Page<DataSourceAlertRecord> getAlertRecords(String probeKey, String status, int pageNum, int pageSize);

    Map<String, Object> getAlertStatistics();
}
