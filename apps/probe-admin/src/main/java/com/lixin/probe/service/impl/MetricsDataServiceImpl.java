package com.lixin.probe.service.impl;

import com.lixin.probe.dto.MetricsData;
import com.lixin.probe.dto.ProbeMetricsSummary;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.mapper.MetricDataMapper;
import com.lixin.probe.service.MetricsDataService;
import com.lixin.probe.service.ProbeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * WebSocket实时指标推送服务实现
 *
 * @author Claude Code
 * @date 2026-04-13
 */
@Service
public class MetricsDataServiceImpl implements MetricsDataService {

    private static final Logger log = LoggerFactory.getLogger(MetricsDataServiceImpl.class);

    @Autowired
    private MetricDataMapper metricDataMapper;

    @Autowired
    private ProbeService probeService;

    @Override
    public MetricsData getMetricsData(Long probeId) {
        try {
            log.debug("获取探针指标数据: probeId={}", probeId);

            // 检查探针是否在线
            Probe probe = probeService.getById(probeId);
            if (probe == null || !"online".equalsIgnoreCase(probe.getStatus())) {
                log.debug("探针不存在或离线: probeId={}", probeId);
                return null;
            }

            // 获取最新的指标摘要
            ProbeMetricsSummary summary = metricDataMapper.getMetricsSummary(probeId);
            if (summary == null) {
                log.debug("未找到指标数据: probeId={}", probeId);
                return null;
            }

            // 转换为MetricsData
            return convertToMetricsData(summary, probe);
        } catch (Exception e) {
            log.error("获取探针指标数据失败: probeId={}", probeId, e);
            return null;
        }
    }

    @Override
    public List<MetricsData> getAllOnlineMetricsData() {
        try {
            log.debug("获取所有在线探针指标数据");

            // 获取所有在线探针
            List<Probe> onlineProbes = probeService.getOnlineProbes();

            return onlineProbes.stream()
                .map(probe -> {
                    try {
                        return getMetricsData(probe.getId());
                    } catch (Exception e) {
                        log.error("获取探针指标数据失败: probeId={}", probe.getId(), e);
                        return null;
                    }
                })
                .filter(data -> data != null)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取所有在线探针指标数据失败", e);
            return List.of();
        }
    }

    /**
     * 将ProbeMetricsSummary转换为MetricsData
     */
    private MetricsData convertToMetricsData(ProbeMetricsSummary summary, Probe probe) {
        MetricsData data = new MetricsData();

        // CPU指标
        data.setCpuUsage(summary.getCpuUsage());
        data.setCpuCores(summary.getCpuCores());
        data.setCpuLoad1min(summary.getCpuLoad1min());
        data.setCpuLoad5min(summary.getCpuLoad5min());
        data.setCpuLoad15min(summary.getCpuLoad15min());

        // 内存指标
        data.setMemoryUsage(summary.getMemoryUsage());
        data.setMemoryTotal(summary.getMemoryTotal());
        data.setMemoryAvailable(summary.getMemoryAvailable());
        data.setMemoryUsed(summary.getMemoryUsed());

        // 磁盘指标
        data.setDiskUsage(summary.getDiskUsage());
        data.setDiskUsed(summary.getDiskUsed());
        data.setDiskTotal(summary.getDiskTotal());

        // 网络指标
        data.setNetworkIn(summary.getNetworkRxRate());
        data.setNetworkOut(summary.getNetworkTxRate());
        data.setNetworkRxBytes(summary.getNetworkRxBytes());
        data.setNetworkTxBytes(summary.getNetworkTxBytes());
        data.setNetworkRxErrors(summary.getNetworkRxErrors() != null ? summary.getNetworkRxErrors().intValue() : null);
        data.setNetworkTxErrors(summary.getNetworkTxErrors() != null ? summary.getNetworkTxErrors().intValue() : null);

        // JVM指标
        data.setJvmHeapUsage(summary.getJvmHeapUsage());
        data.setJvmHeapMax(summary.getJvmHeapMax() != null ? summary.getJvmHeapMax().longValue() : null);
        data.setJvmThreadCount(summary.getJvmThreadCount() != null ? summary.getJvmThreadCount().doubleValue() : null);
        data.setJvmClassLoaded(summary.getJvmClassLoaded() != null ? summary.getJvmClassLoaded().longValue() : null);

        // OS指标
        data.setOsProcessCount(summary.getOsProcessCount());
        data.setOsThreadCount(summary.getOsThreadCount());
        data.setOsUptimeSeconds(summary.getOsUptimeSeconds());

        // 进程指标
        data.setProcessCpuUsage(summary.getProcessCpuUsage());
        data.setProcessMemoryResident(summary.getProcessMemoryResident() != null ? summary.getProcessMemoryResident().longValue() : null);
        data.setProcessJvmUptime(summary.getProcessJvmUptime());
        data.setProcessId(summary.getProcessId() != null ? summary.getProcessId().intValue() : null);

        return data;
    }
}
