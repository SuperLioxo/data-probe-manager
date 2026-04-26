package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.entity.MetricData;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.mapper.MetricDataMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.service.ScheduleEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调度引擎实现 - 定时任务
 */
@Service
public class ScheduleEngineImpl implements ScheduleEngine {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ScheduleEngineImpl.class);

    @Autowired
    private ProbeMapper probeMapper;

    @Autowired
    private MetricDataMapper metricDataMapper;

    /**
     * 探针健康检查 - 每分钟执行
     */
    @Override
    @Scheduled(cron = "0 * * * * ?")
    public void healthCheck() {
        try {
            log.debug("执行探针健康检查...");

            // 检查所有在线探针
            List<Probe> onlineProbes = probeMapper.selectList(
                new LambdaQueryWrapper<Probe>().eq(Probe::getStatus, "online")
            );

            log.debug("当前在线探针数: {}", onlineProbes.size());

        } catch (Exception e) {
            log.error("健康检查异常", e);
        }
    }

    /**
     * 检测离线探针 - 每5分钟执行
     */
    @Override
    @Scheduled(cron = "0 */5 * * * ?")
    public void detectOfflineProbes() {
        try {
            log.info("检测离线探针...");

            // 5分钟未心跳的探针视为离线
            LocalDateTime offlineThreshold = LocalDateTime.now().minusMinutes(5);

            // 查找状态为ONLINE但最后心跳时间超过阈值的探针
            List<Probe> probesToOffline = probeMapper.selectList(
                new LambdaQueryWrapper<Probe>()
                    .eq(Probe::getStatus, "online")
                    .lt(Probe::getLastHeartbeat, offlineThreshold)
            );

            if (!probesToOffline.isEmpty()) {
                for (Probe probe : probesToOffline) {
                    probe.setStatus("offline");
                    probe.setUpdateTime(LocalDateTime.now());
                    probeMapper.updateById(probe);
                    log.warn("探针离线: {} - {}", probe.getProbeKey(), probe.getName());
                }
                log.info("检测到 {} 个离线探针", probesToOffline.size());
            } else {
                log.debug("未检测到离线探针");
            }

        } catch (Exception e) {
            log.error("检测离线探针异常", e);
        }
    }

    /**
     * 清理历史数据 - 每天凌晨2点执行
     */
    @Override
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanHistoryData() {
        try {
            log.info("开始清理历史数据...");

            // 清理90天前的监控数据
            LocalDateTime cleanupThreshold = LocalDateTime.now().minusDays(90);

            // 删除90天前的监控数据
            int deletedCount = metricDataMapper.delete(
                new LambdaQueryWrapper<MetricData>()
                    .lt(MetricData::getTimestamp, cleanupThreshold)
            );

            log.info("历史数据清理完成，共删除 {} 条记录", deletedCount);

        } catch (Exception e) {
            log.error("清理历史数据异常", e);
        }
    }
}
