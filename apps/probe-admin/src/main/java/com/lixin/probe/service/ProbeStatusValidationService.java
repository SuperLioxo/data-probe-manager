package com.lixin.probe.service;

import com.lixin.probe.entity.FileProbe;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.strategy.ProbeHeartbeatStrategy;
import com.lixin.probe.strategy.ProbeStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 探针状态验证服务
 * 根据探针类型使用对应的策略判定是否在线
 */
@Service
public class ProbeStatusValidationService {

    private static final Logger log = LoggerFactory.getLogger(ProbeStatusValidationService.class);

    @Autowired
    @Qualifier("decoratedProbeService")
    private ProbeService probeService;

    @Autowired(required = false)
    private FileProbeService fileProbeService;

    @Autowired
    private ProbeStrategyFactory strategyFactory;

    public boolean isProbeOnline(String probeKey) {
        if (probeKey == null || probeKey.isEmpty()) {
            return false;
        }

        try {
            Probe probe = probeService.getByProbeKey(probeKey);
            if (probe != null) {
                return checkOnline(probe.getStatus(), probe.getType(), probe.getLastHeartbeat());
            }

            if (fileProbeService != null) {
                FileProbe fileProbe = fileProbeService.getByProbeKey(probeKey);
                if (fileProbe != null) {
                    return checkOnline(fileProbe.getStatus(), "FILE", fileProbe.getLastHeartbeat());
                }
            }

            log.debug("探针不存在，判定为离线: probeKey={}", probeKey);
            return false;

        } catch (Exception e) {
            log.error("检查探针在线状态异常: probeKey={}", probeKey, e);
            return false;
        }
    }

    public void validateProbeOnline(String probeKey) {
        if (!isProbeOnline(probeKey)) {
            throw new IllegalStateException("探针离线，无法处理数据: probeKey=" + probeKey);
        }
    }

    public String getProbeStatus(String probeKey) {
        Probe probe = probeService.getByProbeKey(probeKey);
        if (probe != null) {
            return probe.getStatus();
        }

        FileProbe fileProbe = fileProbeService != null ? fileProbeService.getByProbeKey(probeKey) : null;
        if (fileProbe != null) {
            return fileProbe.getStatus();
        }

        return "UNKNOWN";
    }

    private boolean checkOnline(String status, String probeType, LocalDateTime lastHeartbeat) {
        if (!"online".equalsIgnoreCase(status)) {
            return false;
        }

        if (lastHeartbeat == null) {
            return false;
        }

        ProbeHeartbeatStrategy strategy = strategyFactory.getHeartbeatStrategy(probeType);
        int timeoutSeconds = (strategy != null) ? strategy.getTimeoutSeconds() : 90;

        long heartbeatAgeMs = System.currentTimeMillis()
                - lastHeartbeat.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        boolean online = heartbeatAgeMs < timeoutSeconds * 1000L;

        if (!online) {
            log.debug("探针心跳超时: type={}, timeout={}s, age={}ms",
                    probeType, timeoutSeconds, heartbeatAgeMs);
        }

        return online;
    }
}
