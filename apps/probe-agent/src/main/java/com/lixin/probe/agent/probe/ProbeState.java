package com.lixin.probe.agent.probe;

/**
 * 探针状态枚举
 *
 * @author Claude Code
 * @since 1.0
 */
public enum ProbeState {

    /**
     * 未知状态
     */
    UNKNOWN("unknown", "未知"),

    /**
     * 启动中
     */
    STARTING("starting", "启动中"),

    /**
     * 运行中
     */
    RUNNING("running", "运行中"),

    /**
     * 停止中
     */
    STOPPING("stopping", "停止中"),

    /**
     * 已停止
     */
    STOPPED("stopped", "已停止"),

    /**
     * 错误状态
     */
    ERROR("error", "错误"),

    /**
     * 离线
     */
    OFFLINE("offline", "离线");

    private final String code;
    private final String description;

    ProbeState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取状态
     *
     * @param code 状态代码
     * @return 状态枚举，如果不存在返回UNKNOWN
     */
    public static ProbeState fromCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        for (ProbeState state : values()) {
            if (state.code.equals(code)) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
