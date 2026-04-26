package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.ChangeAlertConfig;
import com.lixin.probe.entity.ChangeAlertRecord;
import com.lixin.probe.entity.ChangeLog;
import com.lixin.probe.mapper.ChangeAlertConfigMapper;
import com.lixin.probe.mapper.ChangeAlertRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ChangeAlertServiceImplTest {

    @Mock
    private ChangeAlertConfigMapper alertConfigMapper;

    @Mock
    private ChangeAlertRecordMapper alertRecordMapper;

    @InjectMocks
    private ChangeAlertServiceImpl changeAlertService;

    @Test
    @DisplayName("创建告警配置应设置默认值")
    void testCreateAlertConfig_shouldSetDefaults() {
        ChangeAlertConfig config = ChangeAlertConfig.builder()
                .alertName("测试告警")
                .probeKey("probe-1")
                .alertLevel("WARNING")
                .build();

        when(alertConfigMapper.insert(any(ChangeAlertConfig.class))).thenReturn(1);

        ChangeAlertConfig result = changeAlertService.createAlertConfig(config);

        assertTrue(result.getEnabled(), "Should be enabled by default");
        assertNotNull(result.getCreateTime(), "Should set createTime");
        verify(alertConfigMapper).insert(any(ChangeAlertConfig.class));
    }

    @Test
    @DisplayName("更新告警配置应调用updateById")
    void testUpdateAlertConfig_shouldCallUpdate() {
        ChangeAlertConfig config = ChangeAlertConfig.builder().id(1L).alertName("更新").build();
        changeAlertService.updateAlertConfig(config);
        verify(alertConfigMapper).updateById(config);
    }

    @Test
    @DisplayName("删除告警配置应调用deleteById")
    void testDeleteAlertConfig_shouldCallDelete() {
        changeAlertService.deleteAlertConfig(1L);
        verify(alertConfigMapper).deleteById(1L);
    }

    @Test
    @DisplayName("匹配的变化应触发告警")
    void testProcessChangeLogs_matchingConfig_shouldFireAlert() {
        ChangeAlertConfig config = ChangeAlertConfig.builder()
                .id(1L).probeKey("probe-1").alertLevel("WARNING")
                .changeTypes("ROW_INSERT,ROW_DELETE").thresholdRows(1L)
                .enabled(true).notifyChannels("LOG").build();
        when(alertConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(config));
        when(alertRecordMapper.insert(any(ChangeAlertRecord.class))).thenReturn(1);

        ChangeLog change = ChangeLog.builder()
                .probeKey("probe-1").tableName("orders")
                .changeType("ROW_INSERT").affectedRows(5L)
                .changeDetail("{\"diff\":5}").build();

        changeAlertService.processChangeLogs(List.of(change));

        verify(alertRecordMapper).insert(any(ChangeAlertRecord.class));
    }

    @Test
    @DisplayName("probeKey不匹配不应触发告警")
    void testProcessChangeLogs_probeKeyMismatch_shouldNotFire() {
        ChangeAlertConfig config = ChangeAlertConfig.builder()
                .id(1L).probeKey("probe-other").alertLevel("WARNING")
                .enabled(true).build();
        when(alertConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(config));

        ChangeLog change = ChangeLog.builder()
                .probeKey("probe-1").changeType("ROW_INSERT").affectedRows(5L).build();

        changeAlertService.processChangeLogs(List.of(change));

        verify(alertRecordMapper, never()).insert(any(ChangeAlertRecord.class));
    }

    @Test
    @DisplayName("affectedRows低于阈值不应触发告警")
    void testProcessChangeLogs_belowThreshold_shouldNotFire() {
        ChangeAlertConfig config = ChangeAlertConfig.builder()
                .id(1L).probeKey("probe-1").alertLevel("WARNING")
                .thresholdRows(100L).enabled(true).build();
        when(alertConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(config));

        ChangeLog change = ChangeLog.builder()
                .probeKey("probe-1").changeType("ROW_INSERT").affectedRows(5L).build();

        changeAlertService.processChangeLogs(List.of(change));

        verify(alertRecordMapper, never()).insert(any(ChangeAlertRecord.class));
    }

    @Test
    @DisplayName("changeType不在配置范围不应触发告警")
    void testProcessChangeLogs_changeTypeNotMatch_shouldNotFire() {
        ChangeAlertConfig config = ChangeAlertConfig.builder()
                .id(1L).probeKey("probe-1").alertLevel("WARNING")
                .changeTypes("SIZE_CHANGE,CHECKSUM_CHANGE").enabled(true).build();
        when(alertConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(config));

        ChangeLog change = ChangeLog.builder()
                .probeKey("probe-1").changeType("ROW_INSERT").affectedRows(5L).build();

        changeAlertService.processChangeLogs(List.of(change));

        verify(alertRecordMapper, never()).insert(any(ChangeAlertRecord.class));
    }

    @Test
    @DisplayName("tableName不匹配不应触发告警")
    void testProcessChangeLogs_tableNameMismatch_shouldNotFire() {
        ChangeAlertConfig config = ChangeAlertConfig.builder()
                .id(1L).probeKey("probe-1").tableName("users").alertLevel("WARNING")
                .enabled(true).build();
        when(alertConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(config));

        ChangeLog change = ChangeLog.builder()
                .probeKey("probe-1").tableName("orders").changeType("ROW_INSERT")
                .affectedRows(5L).build();

        changeAlertService.processChangeLogs(List.of(change));

        verify(alertRecordMapper, never()).insert(any(ChangeAlertRecord.class));
    }

    @Test
    @DisplayName("无配置规则时不应触发告警")
    void testProcessChangeLogs_noConfigs_shouldNotFire() {
        when(alertConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ChangeLog change = ChangeLog.builder()
                .probeKey("probe-1").changeType("ROW_INSERT").affectedRows(5L).build();

        changeAlertService.processChangeLogs(List.of(change));

        verify(alertRecordMapper, never()).insert(any(ChangeAlertRecord.class));
    }

    @Test
    @DisplayName("多条变化应逐一匹配")
    void testProcessChangeLogs_multipleChanges_shouldMatchEach() {
        ChangeAlertConfig config = ChangeAlertConfig.builder()
                .id(1L).probeKey("probe-1").alertLevel("WARNING")
                .enabled(true).build();
        when(alertConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(config));
        when(alertRecordMapper.insert(any(ChangeAlertRecord.class))).thenReturn(1);

        ChangeLog change1 = ChangeLog.builder()
                .probeKey("probe-1").changeType("ROW_INSERT").affectedRows(5L).build();
        ChangeLog change2 = ChangeLog.builder()
                .probeKey("probe-1").changeType("SIZE_CHANGE").affectedRows(100L).build();

        changeAlertService.processChangeLogs(List.of(change1, change2));

        verify(alertRecordMapper, times(2)).insert(any(ChangeAlertRecord.class));
    }

    @Test
    @DisplayName("告警记录应设置PENDING状态")
    void testFireAlert_shouldSetPendingStatus() {
        ChangeAlertConfig config = ChangeAlertConfig.builder()
                .id(1L).probeKey("probe-1").alertLevel("CRITICAL")
                .notifyChannels("EMAIL").enabled(true).build();
        when(alertConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(config));

        ArgumentCaptor<ChangeAlertRecord> captor = ArgumentCaptor.forClass(ChangeAlertRecord.class);
        when(alertRecordMapper.insert(captor.capture())).thenReturn(1);

        ChangeLog change = ChangeLog.builder()
                .probeKey("probe-1").tableName("orders").changeType("ROW_INSERT")
                .affectedRows(10L).changeDetail("{}").build();

        changeAlertService.processChangeLogs(List.of(change));

        ChangeAlertRecord record = captor.getValue();
        assertEquals("PENDING", record.getStatus());
        assertEquals("CRITICAL", record.getAlertLevel());
        assertEquals(1L, record.getAlertConfigId());
        assertEquals("probe-1", record.getProbeKey());
        assertEquals("orders", record.getTableName());
        assertNotNull(record.getCreatedTime());
    }

    @Test
    @DisplayName("获取告警统计应返回正确计数")
    void testGetAlertStatistics_shouldReturnCounts() {
        when(alertRecordMapper.selectCount(isNull())).thenReturn(10L);
        when(alertRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L, 7L);
        when(alertConfigMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        Map<String, Object> stats = changeAlertService.getAlertStatistics();

        assertEquals(10L, stats.get("total"));
        assertEquals(3L, stats.get("pending"));
        assertEquals(7L, stats.get("resolved"));
        assertEquals(5L, stats.get("configCount"));
    }

    @Test
    @DisplayName("分页查询告警配置应正确传递参数")
    void testGetAlertConfigs_shouldPaginate() {
        Page<ChangeAlertConfig> mockPage = new Page<>(1, 10);
        when(alertConfigMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<ChangeAlertConfig> result = changeAlertService.getAlertConfigs("probe-1", 1, 10);

        assertNotNull(result);
        verify(alertConfigMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("分页查询告警记录应支持状态过滤")
    void testGetAlertRecords_shouldFilterByStatus() {
        Page<ChangeAlertRecord> mockPage = new Page<>(1, 10);
        when(alertRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<ChangeAlertRecord> result = changeAlertService.getAlertRecords("probe-1", "PENDING", 1, 10);

        assertNotNull(result);
        verify(alertRecordMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }
}
