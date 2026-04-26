package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.ChangeAlertConfig;
import com.lixin.probe.entity.ChangeAlertRecord;
import com.lixin.probe.entity.ChangeLog;

import java.util.List;
import java.util.Map;

public interface ChangeAlertService {

    Page<ChangeAlertConfig> getAlertConfigs(String probeKey, int pageNum, int pageSize);

    ChangeAlertConfig createAlertConfig(ChangeAlertConfig config);

    void updateAlertConfig(ChangeAlertConfig config);

    void deleteAlertConfig(Long id);

    void processChangeLogs(List<ChangeLog> changes);

    Page<ChangeAlertRecord> getAlertRecords(String probeKey, String status, int pageNum, int pageSize);

    Map<String, Object> getAlertStatistics();
}
