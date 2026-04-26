package com.lixin.probe.agent.pojo.report;


import java.util.List;

/**
 * Agent插件上报对象
 * 用于向管理系统上报本地插件信息
 *
 * @author probe-agent
 * @since 1.0.0
 */
public class PluginReport {

    /**
     * 探针编码
     */
    private String agentCode;

    /**
     * 探针主机地址
     */
    private String agentHost;

    /**
     * 探针端口
     */
    private Integer agentPort;

    /**
     * 插件列表
     */
    private List<PluginInfo> plugins;

    /**
     * 上报时间
     */
    private Long reportTime;

    // PluginReport getters/setters
    public String getAgentCode() {
        return agentCode;
    }
    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }
    public String getAgentHost() {
        return agentHost;
    }
    public void setAgentHost(String agentHost) {
        this.agentHost = agentHost;
    }
    public Integer getAgentPort() {
        return agentPort;
    }
    public void setAgentPort(Integer agentPort) {
        this.agentPort = agentPort;
    }
    public List<PluginInfo> getPlugins() {
        return plugins;
    }
    public void setPlugins(List<PluginInfo> plugins) {
        this.plugins = plugins;
    }
    public Long getReportTime() {
        return reportTime;
    }
    public void setReportTime(Long reportTime) {
        this.reportTime = reportTime;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PluginReport report = new PluginReport();

        public Builder agentCode(String agentCode) {
            report.agentCode = agentCode;
            return this;
        }

        public Builder agentHost(String agentHost) {
            report.agentHost = agentHost;
            return this;
        }

        public Builder agentPort(Integer agentPort) {
            report.agentPort = agentPort;
            return this;
        }

        public Builder plugins(List<PluginInfo> plugins) {
            report.plugins = plugins;
            return this;
        }

        public Builder reportTime(Long reportTime) {
            report.reportTime = reportTime;
            return this;
        }

        public PluginReport build() {
            return report;
        }
    }

    /**
     * 插件信息
     */
    public static class PluginInfo {
        /**
         * 插件ID
         */
        private String pluginId;

        /**
         * 插件名称
         */
        private String name;

        /**
         * 插件类型
         */
        private String type;

        /**
         * 插件版本
         */
        private String version;

        /**
         * 数据库类型
         */
        private String dbType;

        /**
         * 支持的版本范围
         */
        private String versionRange;

        /**
         * 插件描述
         */
        private String description;

        /**
         * 插件状态
         */
        private String status;

        /**
         * 加载时间
         */
        private Long loadTime;

        // PluginInfo getters/setters
        public String getPluginId() {
            return pluginId;
        }
        public void setPluginId(String pluginId) {
            this.pluginId = pluginId;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
        public String getVersion() {
            return version;
        }
        public void setVersion(String version) {
            this.version = version;
        }
        public String getDbType() {
            return dbType;
        }
        public void setDbType(String dbType) {
            this.dbType = dbType;
        }
        public String getVersionRange() {
            return versionRange;
        }
        public void setVersionRange(String versionRange) {
            this.versionRange = versionRange;
        }
        public String getDescription() {
            return description;
        }
        public void setDescription(String description) {
            this.description = description;
        }
        public String getStatus() {
            return status;
        }
        public void setStatus(String status) {
            this.status = status;
        }
        public Long getLoadTime() {
            return loadTime;
        }
        public void setLoadTime(Long loadTime) {
            this.loadTime = loadTime;
        }

        // Builder pattern
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private PluginInfo info = new PluginInfo();

            public Builder pluginId(String pluginId) {
                info.pluginId = pluginId;
                return this;
            }
            public Builder name(String name) {
                info.name = name;
                return this;
            }
            public Builder type(String type) {
                info.type = type;
                return this;
            }
            public Builder version(String version) {
                info.version = version;
                return this;
            }
            public Builder dbType(String dbType) {
                info.dbType = dbType;
                return this;
            }
            public Builder versionRange(String versionRange) {
                info.versionRange = versionRange;
                return this;
            }
            public Builder description(String description) {
                info.description = description;
                return this;
            }
            public Builder status(String status) {
                info.status = status;
                return this;
            }
            public Builder loadTime(Long loadTime) {
                info.loadTime = loadTime;
                return this;
            }

            public PluginInfo build() {
                return info;
            }
        }
    }
}
