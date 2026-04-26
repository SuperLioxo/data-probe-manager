package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.dto.ProbeMetricsSummary;
import com.lixin.probe.entity.MetricData;
import com.lixin.probe.mapper.MetricDataMapper;
import com.lixin.probe.service.MetricDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控数据Service实现
 */
@Service
public class MetricDataServiceImpl implements MetricDataService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MetricDataServiceImpl.class);

    @Autowired
    private MetricDataMapper metricDataMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<MetricData> getProbeMetrics(Long probeId, String metricName, String startTime, String endTime) {
        LambdaQueryWrapper<MetricData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetricData::getProbeId, probeId);
        
        if (metricName != null && !metricName.isEmpty()) {
            wrapper.eq(MetricData::getMetricName, metricName);
        }
        
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(MetricData::getTimestamp, LocalDateTime.parse(startTime, FORMATTER));
        }
        
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(MetricData::getTimestamp, LocalDateTime.parse(endTime, FORMATTER));
        }
        
        wrapper.orderByAsc(MetricData::getTimestamp);
        wrapper.last("LIMIT 1000"); // 限制返回1000条数据
        
        return metricDataMapper.selectList(wrapper);
    }

    @Override
    public List<MetricData> getLatestMetrics(Long probeId) {
        // 查询该探针的所有最新指标
        LambdaQueryWrapper<MetricData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetricData::getProbeId, probeId);
        wrapper.orderByDesc(MetricData::getTimestamp);

        // 获取所有指标数据，按时间倒序
        List<MetricData> allMetrics = metricDataMapper.selectList(wrapper);

        // 使用LinkedHashMap保持插入顺序，按指标名称去重，保留最新的
        java.util.LinkedHashMap<String, MetricData> latestMap = new java.util.LinkedHashMap<>();
        for (MetricData metric : allMetrics) {
            if (!latestMap.containsKey(metric.getMetricName())) {
                latestMap.put(metric.getMetricName(), metric);
            }
        }

        return new java.util.ArrayList<>(latestMap.values());
    }

    @Override
    public void save(MetricData metricData) {
        if (metricData.getTimestamp() == null) {
            metricData.setTimestamp(LocalDateTime.now());
        }
        metricDataMapper.insert(metricData);
        log.debug("保存监控数据: probeId={}, metricName={}, value={}",
            metricData.getProbeId(), metricData.getMetricName(), metricData.getMetricValue());
    }

    @Override
    public void batchSave(List<MetricData> metrics) {
        if (metrics != null && !metrics.isEmpty()) {
            // 设置时间戳
            for (MetricData metric : metrics) {
                if (metric.getTimestamp() == null) {
                    metric.setTimestamp(LocalDateTime.now());
                }
            }
            // 批量插入
            for (MetricData metric : metrics) {
                metricDataMapper.insert(metric);
            }
            log.info("批量保存监控数据: {} 条", metrics.size());
        }
    }

    @Override
    public ProbeMetricsSummary getMetricsSummary(Long probeId) {
        // 获取所有最新指标
        List<MetricData> metrics = getLatestMetrics(probeId);

        // 构建指标映射，便于快速查找
        Map<String, MetricData> metricMap = new HashMap<>();
        for (MetricData metric : metrics) {
            metricMap.put(metric.getMetricName(), metric);
        }

        // 构建摘要对象
        ProbeMetricsSummary.ProbeMetricsSummaryBuilder builder = ProbeMetricsSummary.builder();

        // CPU使用率
        MetricData cpuUsageMetric = metricMap.get("cpu.usage");
        if (cpuUsageMetric != null) {
            builder.cpuUsage(cpuUsageMetric.getMetricValue().doubleValue());
        }

        // 内存指标
        MetricData memoryUsageMetric = metricMap.get("memory.usage");
        if (memoryUsageMetric != null) {
            builder.memoryUsage(memoryUsageMetric.getMetricValue().doubleValue());
        }

        MetricData memoryUsedMetric = metricMap.get("memory.used");
        if (memoryUsedMetric != null) {
            builder.memoryUsed(memoryUsedMetric.getMetricValue().longValue());
        }

        MetricData memoryTotalMetric = metricMap.get("memory.total");
        if (memoryTotalMetric != null) {
            builder.memoryTotal(memoryTotalMetric.getMetricValue().longValue());
        }

        MetricData memoryAvailableMetric = metricMap.get("memory.available");
        if (memoryAvailableMetric != null) {
            builder.memoryAvailable(memoryAvailableMetric.getMetricValue().longValue());
        }

        // 磁盘使用率（取第一个disk.usage记录，通常是根分区）
        MetricData diskUsageMetric = metricMap.get("disk.usage");
        if (diskUsageMetric != null && diskUsageMetric.getMetricValue() != null) {
            double diskUsage = diskUsageMetric.getMetricValue().doubleValue();
            // 过滤掉明显异常的值（如0或接近100）
            if (diskUsage > 0 && diskUsage < 100) {
                builder.diskUsage(diskUsage);
                log.debug("磁盘使用率: {}%", diskUsage);
            }
        }

        // 网络速率（直接查询最近的非零速率记录）
        LambdaQueryWrapper<MetricData> networkWrapper = new LambdaQueryWrapper<>();
        networkWrapper.eq(MetricData::getProbeId, probeId);
        networkWrapper.in(MetricData::getMetricName, Arrays.asList("network.rx.rate", "network.tx.rate"));
        networkWrapper.gt(MetricData::getMetricValue, 0); // 只要非零值
        networkWrapper.orderByDesc(MetricData::getTimestamp);
        networkWrapper.last("LIMIT 20"); // 获取最近的20条记录

        log.info("查询网络速率指标: probeId={}, 查询条件={}", probeId, networkWrapper);
        List<MetricData> networkMetrics = metricDataMapper.selectList(networkWrapper);
        log.info("查询到 {} 条网络速率记录", networkMetrics.size());

        // 取每个指标的最新非零值（不是累加）
        double networkRxRate = 0;
        double networkTxRate = 0;
        boolean foundRxRate = false;
        boolean foundTxRate = false;

        for (MetricData metric : networkMetrics) {
            log.info("处理网络速率记录: name={}, value={}", metric.getMetricName(), metric.getMetricValue());
            if (!foundRxRate && "network.rx.rate".equals(metric.getMetricName())) {
                networkRxRate = metric.getMetricValue().doubleValue();
                foundRxRate = true;
                log.info("找到网络RX速率: {}", networkRxRate);
            }
            if (!foundTxRate && "network.tx.rate".equals(metric.getMetricName())) {
                networkTxRate = metric.getMetricValue().doubleValue();
                foundTxRate = true;
                log.info("找到网络TX速率: {}", networkTxRate);
            }
            // 如果两个都找到了，退出循环
            if (foundRxRate && foundTxRate) {
                break;
            }
        }

        if (networkRxRate > 0) {
            builder.networkRxRate(networkRxRate);
            log.info("设置网络下载速率: {} KB/s", String.format("%.2f", networkRxRate / 1024));
        } else {
            log.warn("网络下载速率未找到或为0: networkRxRate={}", networkRxRate);
        }
        if (networkTxRate > 0) {
            builder.networkTxRate(networkTxRate);
            log.info("设置网络上传速率: {} KB/s", String.format("%.2f", networkTxRate / 1024));
        } else {
            log.warn("网络上传速率未找到或为0: networkTxRate={}", networkTxRate);
        }

        // CPU负载平均值
        MetricData cpuLoad1min = metricMap.get("cpu.load.1min");
        if (cpuLoad1min != null) {
            builder.cpuLoad1min(cpuLoad1min.getMetricValue().doubleValue());
        }

        MetricData cpuLoad5min = metricMap.get("cpu.load.5min");
        if (cpuLoad5min != null) {
            builder.cpuLoad5min(cpuLoad5min.getMetricValue().doubleValue());
        }

        MetricData cpuLoad15min = metricMap.get("cpu.load.15min");
        if (cpuLoad15min != null) {
            builder.cpuLoad15min(cpuLoad15min.getMetricValue().doubleValue());
        }

        // CPU核心数
        MetricData cpuCoresMetric = metricMap.get("cpu.cores");
        if (cpuCoresMetric != null) {
            builder.cpuCores(cpuCoresMetric.getMetricValue().intValue());
        }

        // 磁盘空间（取根分区的空间）
        MetricData diskUsedMetric = metricMap.get("disk.used.root");
        if (diskUsedMetric != null) {
            builder.diskUsed(diskUsedMetric.getMetricValue().longValue());
        }

        MetricData diskTotalMetric = metricMap.get("disk.total.root");
        if (diskTotalMetric != null) {
            builder.diskTotal(diskTotalMetric.getMetricValue().longValue());
        }

        // 网络累计字节数（取所有接口的总和）
        long totalRxBytes = 0;
        long totalTxBytes = 0;
        long totalRxErrors = 0;
        long totalTxErrors = 0;

        for (MetricData metric : metrics) {
            String metricName = metric.getMetricName();
            if (metricName != null) {
                if (metricName.startsWith("network.rx.bytes") && metric.getMetricValue() != null) {
                    totalRxBytes += metric.getMetricValue().longValue();
                } else if (metricName.startsWith("network.tx.bytes") && metric.getMetricValue() != null) {
                    totalTxBytes += metric.getMetricValue().longValue();
                } else if (metricName.startsWith("network.rx.errors") && metric.getMetricValue() != null) {
                    totalRxErrors += metric.getMetricValue().longValue();
                } else if (metricName.startsWith("network.tx.errors") && metric.getMetricValue() != null) {
                    totalTxErrors += metric.getMetricValue().longValue();
                }
            }
        }

        if (totalRxBytes > 0) {
            builder.networkRxBytes(totalRxBytes);
        }
        if (totalTxBytes > 0) {
            builder.networkTxBytes(totalTxBytes);
        }
        if (totalRxErrors > 0) {
            builder.networkRxErrors(totalRxErrors);
        }
        if (totalTxErrors > 0) {
            builder.networkTxErrors(totalTxErrors);
        }

        // ==================== JVM 指标 ====================
        MetricData jvmHeapUsedMetric = metricMap.get("jvm.heap.used");
        if (jvmHeapUsedMetric != null) {
            builder.jvmHeapUsed(jvmHeapUsedMetric.getMetricValue().doubleValue());
        }

        MetricData jvmHeapMaxMetric = metricMap.get("jvm.heap.max");
        if (jvmHeapMaxMetric != null) {
            builder.jvmHeapMax(jvmHeapMaxMetric.getMetricValue().doubleValue());
        }

        MetricData jvmHeapUsageMetric = metricMap.get("jvm.heap.usage");
        if (jvmHeapUsageMetric != null) {
            builder.jvmHeapUsage(jvmHeapUsageMetric.getMetricValue().doubleValue());
        }

        MetricData jvmThreadCountMetric = metricMap.get("jvm.thread.count");
        if (jvmThreadCountMetric != null) {
            builder.jvmThreadCount(jvmThreadCountMetric.getMetricValue().intValue());
        }

        MetricData jvmThreadPeakMetric = metricMap.get("jvm.thread.peak");
        if (jvmThreadPeakMetric != null) {
            builder.jvmThreadPeak(jvmThreadPeakMetric.getMetricValue().intValue());
        }

        MetricData jvmClassLoadedMetric = metricMap.get("jvm.class.loaded");
        if (jvmClassLoadedMetric != null) {
            builder.jvmClassLoaded(jvmClassLoadedMetric.getMetricValue().intValue());
        }

        MetricData jvmTotalMemoryMetric = metricMap.get("jvm.runtime.totalMemory");
        if (jvmTotalMemoryMetric != null) {
            builder.jvmTotalMemory(jvmTotalMemoryMetric.getMetricValue().doubleValue());
        }

        MetricData jvmFreeMemoryMetric = metricMap.get("jvm.runtime.freeMemory");
        if (jvmFreeMemoryMetric != null) {
            builder.jvmFreeMemory(jvmFreeMemoryMetric.getMetricValue().doubleValue());
        }

        // ==================== OS 指标 ====================
        MetricData osProcessCountMetric = metricMap.get("os.process.count");
        if (osProcessCountMetric != null) {
            builder.osProcessCount(osProcessCountMetric.getMetricValue().intValue());
        }

        MetricData osThreadCountMetric = metricMap.get("os.thread.count");
        if (osThreadCountMetric != null) {
            builder.osThreadCount(osThreadCountMetric.getMetricValue().intValue());
        }

        MetricData osUptimeMetric = metricMap.get("os.uptime.seconds");
        if (osUptimeMetric != null) {
            builder.osUptimeSeconds(osUptimeMetric.getMetricValue().longValue());
        }

        // ==================== 进程指标 ====================
        MetricData processCpuUsageMetric = metricMap.get("process.cpu.usage");
        if (processCpuUsageMetric != null) {
            builder.processCpuUsage(processCpuUsageMetric.getMetricValue().doubleValue());
        }

        MetricData processMemoryResidentMetric = metricMap.get("process.memory.resident.mb");
        if (processMemoryResidentMetric != null) {
            builder.processMemoryResident(processMemoryResidentMetric.getMetricValue().doubleValue());
        }

        MetricData processJvmUptimeMetric = metricMap.get("process.jvm.uptime.seconds");
        if (processJvmUptimeMetric != null) {
            builder.processJvmUptime(processJvmUptimeMetric.getMetricValue().longValue());
        }

        MetricData processIdMetric = metricMap.get("process.id");
        if (processIdMetric != null) {
            builder.processId(processIdMetric.getMetricValue().longValue());
        }

        // 设置采集时间（使用最新指标的时间）
        if (!metrics.isEmpty()) {
            builder.collectTime(metrics.get(0).getTimestamp());
        } else {
            builder.collectTime(LocalDateTime.now());
        }

        ProbeMetricsSummary summary = builder.build();

        log.debug("获取探针指标摘要: probeId={}, cpuUsage={}%, memoryUsage={}%",
            probeId, summary.getCpuUsage(), summary.getMemoryUsage());

        return summary;
    }
}
