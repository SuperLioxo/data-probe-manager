package com.lixin.probe.agent.pojo.report;


/**
 * 插件状态对象
 * 用于心跳上报
 *
 * @author probe-agent
 * @since 1.0.0
 */
public class PluginStatus {

    /**
     * 探针编码
     */
    private String agentCode;

    /**
     * 插件ID
     */
    private String pluginId;

    /**
     * 插件状态
     */
    private String status;

    /**
     * 心跳时间
     */
    private Long heartbeatTime;

    /**
     * 插件版本（可选）
     */
    private String version;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;
    public String getAgentCode() {
        return agentCode;
    }
    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }
    public String getPluginId() {
        return pluginId;
    }
    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public Long getHeartbeatTime() {
        return heartbeatTime;
    }
    public void setHeartbeatTime(Long heartbeatTime) {
        this.heartbeatTime = heartbeatTime;
    }
    public String getVersion() {
        return version;
    }
    public void setVersion(String version) {
        this.version = version;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentCode;
        private String pluginId;
        private String status;
        private Long heartbeatTime;
        private String version;
        private String errorMessage;

        public Builder agentCode(String agentCode) {
            this.agentCode = agentCode;
            return this;
        }

        public Builder pluginId(String pluginId) {
            this.pluginId = pluginId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder heartbeatTime(Long heartbeatTime) {
            this.heartbeatTime = heartbeatTime;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public PluginStatus build() {
            PluginStatus status = new PluginStatus();
            status.agentCode = this.agentCode;
            status.pluginId = this.pluginId;
            status.status = this.status;
            status.heartbeatTime = this.heartbeatTime;
            status.version = this.version;
            status.errorMessage = this.errorMessage;
            return status;
        }
    }
}
