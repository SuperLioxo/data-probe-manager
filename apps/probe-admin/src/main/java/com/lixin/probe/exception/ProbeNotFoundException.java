package com.lixin.probe.exception;

/**
 * 探针不存在异常
 *
 * @author Claude Code
 * @date 2026-03-11
 */
public class ProbeNotFoundException extends BusinessException {

    public ProbeNotFoundException(String probeKey) {
        super(ErrorCode.PROBE_NOT_FOUND.getCode(),
              String.format(ErrorCode.PROBE_NOT_FOUND.getMessage(), probeKey));
    }

    public ProbeNotFoundException(Long probeId) {
        super(ErrorCode.PROBE_NOT_FOUND.getCode(),
              "探针不存在: ID=" + probeId);
    }
}
