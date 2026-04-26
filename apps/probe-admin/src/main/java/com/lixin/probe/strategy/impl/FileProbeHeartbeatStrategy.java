package com.lixin.probe.strategy.impl;

import com.lixin.probe.entity.Probe;
import com.lixin.probe.strategy.ProbeHeartbeatStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 文件探针心跳策略
 * 文件探针心跳间隔较长（5分钟）
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
public class FileProbeHeartbeatStrategy implements ProbeHeartbeatStrategy {

    private static final Logger log = LoggerFactory.getLogger(FileProbeHeartbeatStrategy.class);

    /**
     * 文件探针超时时间：5分钟（300秒）
     */
    private static final int TIMEOUT_SECONDS = 300;

    @Override
    public boolean handleHeartbeat(Probe probe) {
        if (probe == null) {
            return false;
        }

        log.debug("处理文件探针心跳: key={}", probe.getProbeKey());

        probe.setLastHeartbeat(LocalDateTime.now());
        probe.setStatus("online");

        return true;
    }

    @Override
    public boolean isTimeout(Probe probe) {
        if (probe == null || probe.getLastHeartbeat() == null) {
            return true;
        }

        long minutes = ChronoUnit.MINUTES.between(
            probe.getLastHeartbeat(),
            LocalDateTime.now()
        );

        boolean timeout = minutes > 5;

        if (timeout) {
            log.warn("文件探针超时: key={}, lastHeartbeat={}分钟前",
                    probe.getProbeKey(), minutes);
        }

        return timeout;
    }

    @Override
    public int getTimeoutSeconds() {
        return TIMEOUT_SECONDS;
    }

    @Override
    public String getSupportedType() {
        return "FILE";
    }
}
