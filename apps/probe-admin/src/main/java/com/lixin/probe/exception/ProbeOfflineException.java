package com.lixin.probe.exception;

/**
 * 探针离线异常
 * 当尝试操作离线探针时抛出此异常
 *
 * @author Claude Code
 * @date 2026-04-12
 */
public class ProbeOfflineException extends BusinessException {

    private final String probeKey;

    public ProbeOfflineException(String probeKey) {
        super(503, "探针离线: " + probeKey);
        this.probeKey = probeKey;
    }

    public ProbeOfflineException(String probeKey, String message) {
        super(503, message);
        this.probeKey = probeKey;
    }

    public String getProbeKey() {
        return probeKey;
    }
}
