package com.lixin.probe.exception;

/**
 * 探针已存在异常
 * 用于处理探针Key重复、系统探针IP重复等情况
 *
 * @author Claude Code
 * @date 2026-03-21
 */
public class ProbeAlreadyExistsException extends BusinessException {

    /**
     * 探针Key已存在
     */
    public ProbeAlreadyExistsException(String probeKey) {
        super(ErrorCode.PROBE_KEY_EXISTS.getCode(),
              String.format("探针标识已存在: %s", probeKey));
    }

    /**
     * 系统探针IP已存在
     *
     * @param hostIp IP地址
     * @param existingProbeKey 已存在的探针Key
     * @param existingProbeName 已存在的探针名称
     */
    public ProbeAlreadyExistsException(String hostIp, String existingProbeKey, String existingProbeName) {
        super(ErrorCode.PROBE_SYSTEM_IP_EXISTS.getCode(),
              String.format("该IP地址 %s 已存在系统探针：%s（%s）。每个IP只能创建一个系统探针。",
                          hostIp, existingProbeName, existingProbeKey));
    }

}
