package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lixin.probe.entity.SyncTask;
import com.lixin.probe.mapper.DatabaseConnectionMapper;
import com.lixin.probe.mapper.SyncLogMapper;
import com.lixin.probe.mapper.SyncTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class SyncTaskServiceImplTest {

    @Mock
    private SyncTaskMapper syncTaskMapper;

    @Mock
    private SyncLogMapper syncLogMapper;

    @Mock
    private DatabaseConnectionMapper connectionMapper;

    @InjectMocks
    private SyncTaskServiceImpl syncTaskService;

    @Test
    @DisplayName("创建任务应设置默认值")
    void testCreateTask_shouldSetDefaults() {
        SyncTask task = SyncTask.builder()
                .taskName("测试同步")
                .sourceProbeKey("probe-1")
                .targetType("DATABASE")
                .targetConfig("{}")
                .build();

        when(syncTaskMapper.insert(any(SyncTask.class))).thenReturn(1);

        SyncTask result = syncTaskService.createTask(task);

        assertEquals("INCREMENTAL", result.getSyncMode(), "Should default to INCREMENTAL");
        assertEquals("UPSERT", result.getConflictStrategy(), "Should default to UPSERT");
        assertTrue(result.getEnabled(), "Should be enabled by default");
        assertNotNull(result.getCreateTime());
        assertNotNull(result.getUpdateTime());
    }

    @Test
    @DisplayName("创建带Cron的任务应计算nextSyncTime")
    void testCreateTask_withCron_shouldCalculateNextSync() {
        SyncTask task = SyncTask.builder()
                .taskName("定时同步")
                .sourceProbeKey("probe-1")
                .targetType("DATABASE")
                .targetConfig("{}")
                .cronExpression("0 0 * * * ?")
                .build();

        when(syncTaskMapper.insert(any(SyncTask.class))).thenReturn(1);
        when(syncTaskMapper.updateById(any(SyncTask.class))).thenReturn(1);

        syncTaskService.createTask(task);

        verify(syncTaskMapper, atLeastOnce()).updateById(any(SyncTask.class));
    }

    @Test
    @DisplayName("获取统计应返回各状态计数")
    void testGetSyncStatistics_shouldReturnCounts() {
        when(syncTaskMapper.selectCount(any())).thenReturn(5L, 3L, 1L, 1L, 0L);
        when(syncLogMapper.selectCount(any())).thenReturn(10L);

        var stats = syncTaskService.getSyncStatistics();

        assertNotNull(stats);
        assertEquals(5L, stats.get("total"));
        assertNotNull(stats.get("enabled"));
        assertNotNull(stats.get("success"));
        assertNotNull(stats.get("failed"));
        assertNotNull(stats.get("disabled"));
        assertNotNull(stats.get("running"));
        assertEquals(10L, stats.get("totalLogs"));
    }

    @Test
    @DisplayName("删除任务应同时删除相关日志")
    void testDeleteTask_shouldCascadeDeleteLogs() {
        when(syncTaskMapper.deleteById(1L)).thenReturn(1);

        syncTaskService.deleteTask(1L);

        verify(syncLogMapper).delete(any());
    }

    @Test
    @DisplayName("触发同步：任务不存在时应抛异常")
    void testTriggerSync_taskNotFound_shouldThrow() {
        when(syncTaskMapper.selectById(anyLong())).thenReturn(null);

        assertThrows(RuntimeException.class, () -> syncTaskService.triggerSync(999L));
    }

    @Test
    @DisplayName("触发同步：任务禁用时应抛异常")
    void testTriggerSync_disabledTask_shouldThrow() {
        SyncTask disabled = SyncTask.builder().id(1L).enabled(false).build();
        when(syncTaskMapper.selectById(anyLong())).thenReturn(disabled);

        assertThrows(RuntimeException.class, () -> syncTaskService.triggerSync(1L));
    }

    // Note: toggleTask and updateNextSyncTimes use LambdaUpdateWrapper which requires
    // MyBatis-Plus entity cache initialized by Spring context. These are tested via
    // integration tests with @SpringBootTest instead.

}
