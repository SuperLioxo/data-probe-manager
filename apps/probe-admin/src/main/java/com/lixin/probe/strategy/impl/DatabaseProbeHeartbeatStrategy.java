package com.lixin.probe.strategy.impl;

import com.lixin.probe.entity.Probe;
import com.lixin.probe.strategy.ProbeHeartbeatStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 数据库探针心跳策略
 * 数据库探针心跳间隔较短（1分钟）
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
public class DatabaseProbeHeartbeatStrategy implements ProbeHeartbeatStrategy {

    private static final Logger log = LoggerFactory.getLogger(DatabaseProbeHeartbeatStrategy.class);

    /**
     * 数据库探针超时时间：1分钟（60秒）
     */
    private static final int TIMEOUT_SECONDS = 60;

    @Override
    public boolean handleHeartbeat(Probe probe) {
        if (probe == null) {
            return false;
        }

        log.debug("处理数据库探针心跳: key={}", probe.getProbeKey());

        probe.setLastHeartbeat(LocalDateTime.now());
        probe.setStatus("online");

        return true;
    }

    @Override
    public boolean isTimeout(Probe probe) {
        if (probe == null || probe.getLastHeartbeat() == null) {
            return true;
        }

        long seconds = ChronoUnit.SECONDS.between(
            probe.getLastHeartbeat(),
            LocalDateTime.now()
        );

        boolean timeout = seconds > 60;

        if (timeout) {
            log.warn("数据库探针超时: key={}, lastHeartbeat={}秒前",
                    probe.getProbeKey(), seconds);
        }

        return timeout;
    }

    @Override
    public int getTimeoutSeconds() {
        return TIMEOUT_SECONDS;
    }

    @Override
    public String getSupportedType() {
        return "DATABASE";
    }
}
