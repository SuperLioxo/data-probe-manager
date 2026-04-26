package com.lixin.probe.service;

import com.lixin.probe.dto.MetricsData;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.websocket.MetricsWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时指标推送服务
 *
 * 功能：
 * 1. 定期收集探针指标数据
 * 2. 推送到WebSocket连接的客户端
 * 3. 检测告警并推送
 *
 * @author Claude Code
 * @date 2026-04-13
 */
@Service
public class MetricsPushService {

    private static final Logger log = LoggerFactory.getLogger(MetricsPushService.class);

    @Autowired
    private MetricsDataService metricsDataService;

    @Autowired
    private ProbeService probeService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private MetricsWebSocketHandler metricsWebSocketHandler;

    // 缓存上次的指标数据，用于检测变化
    private final Map<Long, MetricsData> lastMetricsData = new ConcurrentHashMap<>();

    /**
     * 收集指定探针的指标数据
     */
    public MetricsData collectMetrics(Long probeId) {
        try {
            log.debug("收集探针指标数据: probeId={}", probeId);

            // 获取探针信息
            Probe probe = probeService.getById(probeId);
            if (probe == null || !"online".equalsIgnoreCase(probe.getStatus())) {
                log.debug("探针不存在或离线: probeId={}", probeId);
                return null;
            }

            // 收集指标数据
            MetricsData metricsData = metricsDataService.getMetricsData(probeId);
            if (metricsData != null) {
                // 检查是否有重大变化
                if (hasSignificantChange(probeId, metricsData)) {
                    // 更新缓存
                    lastMetricsData.put(probeId, metricsData);

                    // 检查告警
                    checkAndPushAlert(probe, metricsData);
                }
            }

            return metricsData;
        } catch (Exception e) {
            log.error("收集探针指标数据失败: probeId={}", probeId, e);
            return null;
        }
    }

    /**
     * 检查是否有重大变化
     */
    private boolean hasSignificantChange(Long probeId, MetricsData newMetrics) {
        MetricsData oldMetrics = lastMetricsData.get(probeId);

        if (oldMetrics == null) {
            return true; // 首次收集，认为有变化
        }

        // 检查关键指标变化幅度
        double cpuChange = Math.abs(newMetrics.getCpuUsage() - oldMetrics.getCpuUsage());
        double memoryChange = Math.abs(newMetrics.getMemoryUsage() - oldMetrics.getMemoryUsage());
        double diskChange = Math.abs(newMetrics.getDiskUsage() - oldMetrics.getDiskUsage());

        // 变化超过10%则认为有重大变化
        return cpuChange > 10 || memoryChange > 10 || diskChange > 10;
    }

    /**
     * 检查告警并推送
     */
    private void checkAndPushAlert(Probe probe, MetricsData metricsData) {
        try {
            // 检查CPU告警
            if (metricsData.getCpuUsage() > 90) {
                metricsWebSocketHandler.pushAlert(
                    String.format("探针 %s CPU使用率过高: %.1f%%",
                        probe.getName(), metricsData.getCpuUsage()),
                    "critical"
                );
            }

            // 检查内存告警
            if (metricsData.getMemoryUsage() > 85) {
                metricsWebSocketHandler.pushAlert(
                    String.format("探针 %s 内存使用率过高: %.1f%%",
                        probe.getName(), metricsData.getMemoryUsage()),
                    "warning"
                );
            }

            // 检查磁盘告警
            if (metricsData.getDiskUsage() > 80) {
                metricsWebSocketHandler.pushAlert(
                    String.format("探针 %s 磁盘使用率过高: %.1f%%",
                        probe.getName(), metricsData.getDiskUsage()),
                    "warning"
                );
            }

            // 检查网络错误
            if (metricsData.getNetworkRxErrors() > 0 || metricsData.getNetworkTxErrors() > 0) {
                metricsWebSocketHandler.pushAlert(
                    String.format("探针 %s 检测到网络错误: RX=%d, TX=%d",
                        probe.getName(),
                        metricsData.getNetworkRxErrors(),
                        metricsData.getNetworkTxErrors()),
                    "error"
                );
            }
        } catch (Exception e) {
            log.error("检查告警失败: probeId={}", probe.getId(), e);
        }
    }

    /**
     * 定期推送所有在线探针的指标数据
     * 每10秒执行一次
     */
    @Scheduled(fixedRate = 10000) // 10秒
    public void pushAllOnlineProbesMetrics() {
        try {
            if (metricsWebSocketHandler.getActiveSessionCount() == 0) {
                log.debug("没有WebSocket连接，跳过推送");
                return;
            }

            log.debug("开始推送所有在线探针指标");

            // 获取所有在线探针
            List<Probe> onlineProbes = probeService.getOnlineProbes();

            int pushCount = 0;
            for (Probe probe : onlineProbes) {
                MetricsData metricsData = collectMetrics(probe.getId());
                if (metricsData != null) {
                    // 推送指标更新
                    metricsWebSocketHandler.pushMetricsUpdate(probe.getId(), metricsData);
                    pushCount++;
                }
            }

            log.debug("推送完成: {} 个探针", pushCount);
        } catch (Exception e) {
            log.error("定期推送指标数据失败", e);
        }
    }

    /**
     * 推送探针状态更新
     */
    public void pushStatusUpdate(Long probeId, String status) {
        try {
            metricsWebSocketHandler.pushStatusUpdate(probeId, status);
        } catch (Exception e) {
            log.error("推送状态更新失败: probeId={}, status={}", probeId, status, e);
        }
    }

    /**
     * 获取当前连接数
     */
    public int getActiveConnectionCount() {
        return metricsWebSocketHandler.getActiveSessionCount();
    }
}
