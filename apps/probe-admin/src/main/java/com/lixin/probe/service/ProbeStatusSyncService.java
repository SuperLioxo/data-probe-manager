package com.lixin.probe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.entity.FileProbe;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.mapper.FileProbeMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.strategy.ProbeHeartbeatStrategy;
import com.lixin.probe.strategy.ProbeStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 探针状态同步服务
 * 定期检查探针心跳时间，按探针类型使用对应的超时策略自动更新离线状态
 */
@Service
public class ProbeStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProbeStatusSyncService.class);

    @Autowired
    private ProbeMapper probeMapper;

    @Autowired
    private FileProbeMapper fileProbeMapper;

    @Autowired
    private ProbeStrategyFactory strategyFactory;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Scheduled(fixedRate = 30000)
    public void syncProbeStatus() {
        try {
            int totalUpdatedCount = 0;

            // 同步 probe 表（SYSTEM / DATABASE 类型）
            List<Probe> onlineProbes = probeMapper.selectList(
                new LambdaQueryWrapper<Probe>().eq(Probe::getStatus, "online")
            );

            for (Probe probe : onlineProbes) {
                if (isProbeExpired(probe.getType(), probe.getLastHeartbeat())) {
                    try {
                        probe.setStatus("offline");
                        probeMapper.updateById(probe);
                        totalUpdatedCount++;
                        log.info("[探针状态同步] Probe已离线: probeKey={}, type={}, lastHeartbeat={}",
                                probe.getProbeKey(), probe.getType(), probe.getLastHeartbeat());
                    } catch (Exception e) {
                        log.error("[探针状态同步] 更新Probe状态失败: probeKey={}", probe.getProbeKey(), e);
                    }
                }
            }

            // 同步 file_probe 表（FILE 类型，固定使用 FILE 策略超时）
            List<FileProbe> onlineFileProbes = fileProbeMapper.selectList(
                new LambdaQueryWrapper<FileProbe>().eq(FileProbe::getStatus, "online")
            );

            for (FileProbe probe : onlineFileProbes) {
                if (isProbeExpired("FILE", probe.getLastHeartbeat())) {
                    try {
                        probe.setStatus("offline");
                        fileProbeMapper.updateById(probe);
                        totalUpdatedCount++;
                        log.info("[探针状态同步] FileProbe已离线: probeKey={}, lastHeartbeat={}",
                                probe.getProbeKey(), probe.getLastHeartbeat());
                    } catch (Exception e) {
                        log.error("[探针状态同步] 更新FileProbe状态失败: probeKey={}", probe.getProbeKey(), e);
                    }
                }
            }

            if (totalUpdatedCount > 0) {
                log.info("[探针状态同步] 已将 {} 个过期探针更新为离线状态", totalUpdatedCount);
                evictProbeCache();
            } else {
                log.debug("[探针状态同步] 没有过期的探针需要更新");
            }

        } catch (Exception e) {
            log.error("[探针状态同步] 同步探针状态失败", e);
        }
    }

    private boolean isProbeExpired(String probeType, LocalDateTime lastHeartbeat) {
        if (lastHeartbeat == null) {
            return true;
        }

        ProbeHeartbeatStrategy strategy = strategyFactory.getHeartbeatStrategy(probeType);
        int timeoutSeconds = (strategy != null) ? strategy.getTimeoutSeconds() : 90;

        long heartbeatAgeMs = Instant.now().toEpochMilli()
                - lastHeartbeat.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        return heartbeatAgeMs > timeoutSeconds * 1000L;
    }

    private void evictProbeCache() {
        if (cacheManager == null) {
            return;
        }
        try {
            org.springframework.cache.Cache probeCache = cacheManager.getCache("probe");
            if (probeCache != null) {
                probeCache.clear();
            }
            org.springframework.cache.Cache listCache = cacheManager.getCache("probeList");
            if (listCache != null) {
                listCache.clear();
            }
        } catch (Exception e) {
            log.warn("[探针状态同步] 清除缓存失败", e);
        }
    }
}
