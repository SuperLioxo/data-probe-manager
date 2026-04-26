package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.SyncLog;
import com.lixin.probe.entity.SyncTask;

import java.util.List;
import java.util.Map;

public interface SyncTaskService {

    Page<SyncTask> getTasks(String probeKey, String status, int pageNum, int pageSize);

    SyncTask getTask(Long id);

    SyncTask createTask(SyncTask task);

    SyncTask updateTask(SyncTask task);

    void deleteTask(Long id);

    void toggleTask(Long id, boolean enabled);

    void triggerSync(Long id);

    Page<SyncLog> getSyncLogs(Long taskId, String status, int pageNum, int pageSize);

    Map<String, Object> getSyncStatistics();

    List<SyncTask> getEnabledTasks();

    void executeSync(SyncTask task);

    void updateNextSyncTimes();
}
