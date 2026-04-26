package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lixin.probe.entity.MetricData;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.mapper.MetricDataBatchMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.service.MetricDataOptimizedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 监控数据服务（优化版）
 * 解决N+1查询问题，提供批量查询支持
 *
 * @author Claude Code
 * @date 2026-04-12
 */
@Slf4j
@Service
public class MetricDataOptimizedServiceImpl extends ServiceImpl<MetricDataBatchMapper, MetricData>
        implements MetricDataOptimizedService {

    @Autowired
    private MetricDataBatchMapper metricDataBatchMapper;

    @Autowired
    private ProbeMapper probeMapper;

    /**
     * 批量获取探针详情及其最新指标
     * 解决N+1查询问题
     *
     * @param probeIds 探针ID列表
     * @return 探针详情及指标映射
     */
    @Override
    @Cacheable(value = "probe", key = "'metrics_batch_' + #probeIds.hashCode()")
    public Map<Long, List<MetricData>> getProbeMetricsBatch(List<Long> probeIds) {
        if (probeIds == null || probeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        log.debug("批量查询探针指标数据: probeCount={}", probeIds.size());

        // 批量查询指标数据（1次查询）
        List<MetricData> allMetrics = metricDataBatchMapper.selectLatestByProbeIds(probeIds, 10);

        // 按探针ID分组
        return allMetrics.stream()
                .collect(Collectors.groupingBy(MetricData::getProbeId));
    }

    /**
     * 批量获取探针详情（含指标）
     * 使用批量查询避免N+1问题
     *
     * @param probeIds 探针ID列表
     * @return 探针详情列表
     */
    @Override
    @Cacheable(value = "probe", key = "'details_batch_' + #probeIds.hashCode()")
    public List<ProbeDetailsDTO> getProbeDetailsBatch(List<Long> probeIds) {
        if (probeIds == null || probeIds.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("批量查询探针详情: probeCount={}", probeIds.size());

        // 第1次查询：批量查询探针信息
        List<Probe> probes = probeMapper.selectBatchIds(probeIds);
        Map<Long, Probe> probeMap = probes.stream()
                .collect(Collectors.toMap(Probe::getId, Function.identity()));

        // 第2次查询：批量查询指标数据
        List<MetricData> allMetrics = metricDataBatchMapper.selectLatestByProbeIds(probeIds, 10);
        Map<Long, List<MetricData>> metricsMap = allMetrics.stream()
                .collect(Collectors.groupingBy(MetricData::getProbeId));

        // 组装结果
        return probeIds.stream()
                .map(probeId -> {
                    Probe probe = probeMap.get(probeId);
                    if (probe == null) {
                        return null;
                    }

                    List<MetricData> metrics = metricsMap.getOrDefault(probeId, Collections.emptyList());
                    return new ProbeDetailsDTO(probe, metrics);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取探针指标摘要（带缓存）
     *
     * @param probeId 探针ID
     * @return 指标摘要
     */
    @Override
    @Cacheable(value = "probe", key = "'summary_' + #probeId", unless = "#result == null")
    public MetricDataSummary getProbeMetricsSummary(Long probeId) {
        log.debug("获取探针指标摘要: probeId={}", probeId);

        // 查询最新指标（限制10条）
        List<MetricData> metrics = metricDataBatchMapper.selectLatestByProbeIds(
                Collections.singletonList(probeId), 10);

        if (metrics.isEmpty()) {
            return new MetricDataSummary();
        }

        // 计算摘要
        MetricDataSummary summary = new MetricDataSummary();
        summary.setProbeId(probeId);
        summary.setRecordCount(metrics.size());
        summary.setLatestTimestamp(metrics.get(0).getTimestamp());

        // 按指标名称分组统计
        Map<String, List<MetricData>> metricsByName = metrics.stream()
                .collect(Collectors.groupingBy(MetricData::getMetricName));

        summary.setMetricCount(metricsByName.size());

        // 计算平均值
        Map<String, Double> averages = new HashMap<>();
        metricsByName.forEach((name, dataList) -> {
            double avg = dataList.stream()
                    .mapToDouble(data -> data.getMetricValue() != null ? data.getMetricValue().doubleValue() : 0.0)
                    .average()
                    .orElse(0.0);
            averages.put(name, avg);
        });
        summary.setAverages(averages);

        return summary;
    }

    /**
     * 清除探针缓存
     *
     * @param probeId 探针ID
     */
    @Override
    @CacheEvict(value = "probe", allEntries = true)
    public void evictProbeCache(Long probeId) {
        log.debug("清除探针缓存: probeId={}", probeId);
    }

    /**
     * 批量保存指标数据（性能优化）
     *
     * @param metrics 指标数据列表
     * @return 保存数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "probe", allEntries = true)
    public int batchSave(List<MetricData> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }

        log.debug("批量保存指标数据: count={}", metrics.size());

        // 使用MyBatis Plus的批量保存
        return Integer.parseInt(String.valueOf(saveBatch(metrics)));
    }

    /**
     * 探针详情DTO
     */
    public static class ProbeDetailsDTO {
        private Probe probe;
        private List<MetricData> metrics;

        public ProbeDetailsDTO(Probe probe, List<MetricData> metrics) {
            this.probe = probe;
            this.metrics = metrics;
        }

        public Probe getProbe() {
            return probe;
        }

        public List<MetricData> getMetrics() {
            return metrics;
        }
    }

    /**
     * 指标数据摘要DTO
     */
    public static class MetricDataSummary {
        private Long probeId;
        private int recordCount;
        private int metricCount;
        private LocalDateTime latestTimestamp;
        private Map<String, Double> averages;

        public MetricDataSummary() {
            this.averages = new HashMap<>();
        }

        // Getters and Setters
        public Long getProbeId() {
            return probeId;
        }

        public void setProbeId(Long probeId) {
            this.probeId = probeId;
        }

        public int getRecordCount() {
            return recordCount;
        }

        public void setRecordCount(int recordCount) {
            this.recordCount = recordCount;
        }

        public int getMetricCount() {
            return metricCount;
        }

        public void setMetricCount(int metricCount) {
            this.metricCount = metricCount;
        }

        public LocalDateTime getLatestTimestamp() {
            return latestTimestamp;
        }

        public void setLatestTimestamp(LocalDateTime latestTimestamp) {
            this.latestTimestamp = latestTimestamp;
        }

        public Map<String, Double> getAverages() {
            return averages;
        }

        public void setAverages(Map<String, Double> averages) {
            this.averages = averages;
        }
    }
}
