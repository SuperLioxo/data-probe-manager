package com.lixin.probe.dto;

import java.io.Serializable;

/**
 * 数据库类型信息DTO
 * 用于向前端返回支持的数据库类型及其配置信息
 *
 * @author Claude Code
 * @date 2026-04-08
 */
public class DatabaseTypeInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据库类型标识（如：mysql, postgresql, oracle）
     */
    private String type;

    /**
     * 数据库类型显示名称（如：MySQL, PostgreSQL, Oracle）
     */
    private String label;

    /**
     * 默认端口号
     */
    private Integer defaultPort;

    /**
     * 支持的版本范围
     */
    private String versionRange;

    /**
     * 数据库描述
     */
    private String description;

    public DatabaseTypeInfo() {
    }

    public DatabaseTypeInfo(String type, String label, Integer defaultPort, String versionRange, String description) {
        this.type = type;
        this.label = label;
        this.defaultPort = defaultPort;
        this.versionRange = versionRange;
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getDefaultPort() {
        return defaultPort;
    }

    public void setDefaultPort(Integer defaultPort) {
        this.defaultPort = defaultPort;
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

    @Override
    public String toString() {
        return "DatabaseTypeInfo{" +
                "type='" + type + '\'' +
                ", label='" + label + '\'' +
                ", defaultPort=" + defaultPort +
                ", versionRange='" + versionRange + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
