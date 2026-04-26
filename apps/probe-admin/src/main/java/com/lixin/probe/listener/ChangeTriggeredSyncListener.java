package com.lixin.probe.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.entity.SyncTask;
import com.lixin.probe.mapper.SyncTaskMapper;
import com.lixin.probe.service.SyncTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 变更触发同步监听器
 * 当 CDC 检测到数据变更时，检查是否有启用了实时同步的任务，并立即触发同步
 */
@Slf4j
@Component
public class ChangeTriggeredSyncListener {

    @Autowired
    private SyncTaskMapper syncTaskMapper;

    @Autowired
    private SyncTaskService syncTaskService;

    @Async
    @EventListener
    public void onCDCChange(CDCChangeEvent event) {
        String probeKey = event.getProbeKey();
        String tableName = event.getTableName();

        List<SyncTask> realtimeTasks = syncTaskMapper.selectList(
                new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getSourceProbeKey, probeKey)
                        .eq(SyncTask::getSourceTableName, tableName)
                        .eq(SyncTask::getEnabled, true)
                        .eq(SyncTask::getRealtimeSyncEnabled, true));

        for (SyncTask task : realtimeTasks) {
            try {
                log.info("[实时同步] CDC变更触发同步: taskId={}, table={}", task.getId(), tableName);
                syncTaskService.triggerSync(task.getId());
            } catch (Exception e) {
                log.error("[实时同步] 触发同步失败: taskId={}, error={}", task.getId(), e.getMessage());
            }
        }
    }

    /**
     * CDC 变更事件
     */
    public static class CDCChangeEvent {
        private final String probeKey;
        private final String tableName;
        private final String operation;

        public CDCChangeEvent(String probeKey, String tableName, String operation) {
            this.probeKey = probeKey;
            this.tableName = tableName;
            this.operation = operation;
        }

        public String getProbeKey() { return probeKey; }
        public String getTableName() { return tableName; }
        public String getOperation() { return operation; }
    }
}
