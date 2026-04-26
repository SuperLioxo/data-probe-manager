package com.lixin.probe.exception;

/**
 * Agent离线异常
 * 当尝试与离线的Agent通信时抛出
 *
 * @author probe-admin
 * @since 1.0.0
 */
public class AgentOfflineException extends BusinessException {

    private static final int AGENT_OFFLINE_CODE = 3502;
    private final String agentCode;

    public AgentOfflineException(String agentCode) {
        super(AGENT_OFFLINE_CODE,
             String.format("Agent离线，无法执行命令: agentCode=%s", agentCode));
        this.agentCode = agentCode;
    }

    public AgentOfflineException(String agentCode, String message) {
        super(AGENT_OFFLINE_CODE, message);
        this.agentCode = agentCode;
    }

    public String getAgentCode() {
        return agentCode;
    }
}
