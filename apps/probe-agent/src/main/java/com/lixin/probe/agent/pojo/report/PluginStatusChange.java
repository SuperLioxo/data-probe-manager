package com.lixin.probe.agent.pojo.report;


/**
 * 插件状态变更上报对象
 *
 * @author probe-agent
 * @since 1.0.0
 */
public class PluginStatusChange {

    /**
     * 探针编码
     */
    private String agentCode;

    /**
     * 插件ID
     */
    private String pluginId;

    /**
     * 旧状态
     */
    private String oldStatus;

    /**
     * 新状态
     */
    private String newStatus;

    /**
     * 变更原因
     */
    private String changeReason;

    /**
     * 变更时间
     */
    private Long changeTime;

    /**
     * 操作者
     */
    private String operator;
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
    public String getOldStatus() {
        return oldStatus;
    }
    public void setOldStatus(String oldStatus) {
        this.oldStatus = oldStatus;
    }
    public String getNewStatus() {
        return newStatus;
    }
    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }
    public String getChangeReason() {
        return changeReason;
    }
    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }
    public Long getChangeTime() {
        return changeTime;
    }
    public void setChangeTime(Long changeTime) {
        this.changeTime = changeTime;
    }
    public String getOperator() {
        return operator;
    }
    public void setOperator(String operator) {
        this.operator = operator;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PluginStatusChange change = new PluginStatusChange();

        public Builder agentCode(String agentCode) {
            change.agentCode = agentCode;
            return this;
        }

        public Builder pluginId(String pluginId) {
            change.pluginId = pluginId;
            return this;
        }

        public Builder oldStatus(String oldStatus) {
            change.oldStatus = oldStatus;
            return this;
        }

        public Builder newStatus(String newStatus) {
            change.newStatus = newStatus;
            return this;
        }

        public Builder changeReason(String changeReason) {
            change.changeReason = changeReason;
            return this;
        }

        public Builder changeTime(Long changeTime) {
            change.changeTime = changeTime;
            return this;
        }

        public Builder operator(String operator) {
            change.operator = operator;
            return this;
        }

        public PluginStatusChange build() {
            return change;
        }
    }
}
