package com.lixin.probe.service.impl;

import com.lixin.probe.entity.MetricData;
import com.lixin.probe.mapper.MetricDataBatchMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.service.impl.MetricDataOptimizedServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * MetricDataOptimizedService 单元测试
 * 测试批量查询优化逻辑
 *
 * @author Claude Code
 * @date 2026-04-12
 */
@ExtendWith(MockitoExtension.class)
class MetricDataOptimizedServiceTest {

    @Mock
    private MetricDataBatchMapper metricDataBatchMapper;

    @Mock
    private ProbeMapper probeMapper;

    @InjectMocks
    private MetricDataOptimizedServiceImpl service;

    private List<Long> probeIds;
    private List<MetricData> mockMetrics;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        probeIds = Arrays.asList(1L, 2L, 3L);

        // 创建模拟指标数据
        mockMetrics = Arrays.asList(
            createMetricData(1L, "cpu_usage", 75.5),
            createMetricData(1L, "memory_usage", 60.2),
            createMetricData(2L, "cpu_usage", 45.3),
            createMetricData(2L, "memory_usage", 55.1),
            createMetricData(3L, "cpu_usage", 85.7),
            createMetricData(3L, "memory_usage", 70.4)
        );
    }

    @Test
    void testGetProbeMetricsBatch_Success() {
        // Given
        when(metricDataBatchMapper.selectLatestByProbeIds(anyList(), anyInt()))
            .thenReturn(mockMetrics);

        // When
        Map<Long, List<MetricData>> result = service.getProbeMetricsBatch(probeIds);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(2, result.get(1L).size());
        assertEquals(2, result.get(2L).size());
        assertEquals(2, result.get(3L).size());

        verify(metricDataBatchMapper, times(1)).selectLatestByProbeIds(probeIds, 10);
    }

    @Test
    void testGetProbeMetricsBatch_EmptyList() {
        // Given
        when(metricDataBatchMapper.selectLatestByProbeIds(anyList(), anyInt()))
            .thenReturn(Collections.emptyList());

        // When
        Map<Long, List<MetricData>> result = service.getProbeMetricsBatch(probeIds);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetProbeMetricsBatch_NullInput() {
        // When
        Map<Long, List<MetricData>> result = service.getProbeMetricsBatch(null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // 验证没有调用mapper
        verify(metricDataBatchMapper, never()).selectLatestByProbeIds(anyList(), anyInt());
    }

    @Test
    void testGetProbeMetricsSummary_Success() {
        // Given
        when(metricDataBatchMapper.selectLatestByProbeIds(anyList(), anyInt()))
            .thenReturn(mockMetrics);

        // When
        Object summary = service.getProbeMetricsSummary(1L);

        // Then
        assertNotNull(summary);
        // 验证返回的是MetricDataSummary实例
        assertTrue(summary.getClass().getName().contains("MetricDataSummary"));
    }

    @Test
    void testGetProbeMetricsSummary_NoData() {
        // Given
        when(metricDataBatchMapper.selectLatestByProbeIds(anyList(), anyInt()))
            .thenReturn(Collections.emptyList());

        // When
        Object summary = service.getProbeMetricsSummary(1L);

        // Then
        assertNotNull(summary);
    }

    @Test
    void testBatchSave_NullInput() {
        // When
        int result = service.batchSave(null);

        // Then
        assertEquals(0, result);
    }

    @Test
    void testEvictProbeCache() {
        // When
        service.evictProbeCache(1L);

        // Then
        // 验证方法执行无异常
        // 实际的缓存清除由@CacheEvict注解处理
        assertDoesNotThrow(() -> service.evictProbeCache(1L));
    }

    /**
     * 创建测试用的MetricData对象
     */
    private MetricData createMetricData(Long probeId, String metricName, double value) {
        MetricData metric = new MetricData();
        metric.setProbeId(probeId);
        metric.setMetricName(metricName);
        metric.setMetricValue(BigDecimal.valueOf(value));
        metric.setTimestamp(LocalDateTime.now());
        return metric;
    }
}
