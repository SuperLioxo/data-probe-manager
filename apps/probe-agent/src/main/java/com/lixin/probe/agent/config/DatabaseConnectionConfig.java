package com.lixin.probe.agent.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 数据库连接配置类
 * 用于管理Agent端的数据库连接信息
 *
 * @author Claude Code
 * @since 1.0
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseConnectionConfig {

    /**
     * 实例唯一标识
     */
    @JsonProperty("instanceId")
    private String instanceId;

    /**
     * 探针Key（由Admin服务分配，用于心跳上报）
     */
    @JsonProperty("probeKey")
    private String probeKey;

    /**
     * 数据库类型
     */
    @JsonProperty("databaseType")
    private String databaseType;

    /**
     * 数据库主机地址
     */
    @JsonProperty("host")
    private String host;

    /**
     * 数据库端口
     */
    @JsonProperty("port")
    private Integer port;

    /**
     * 数据库名称
     */
    @JsonProperty("databaseName")
    private String databaseName;

    /**
     * 用户名
     */
    @JsonProperty("username")
    private String username;

    /**
     * 密码
     */
    @JsonProperty("password")
    private String password;

    /**
     * 要监控的Schema列表
     */
    @JsonProperty("schemas")
    private List<String> schemas;

    /**
     * 是否启用
     */
    @JsonProperty("enabled")
    private Boolean enabled;

    /**
     * 描述信息
     */
    @JsonProperty("description")
    private String description;

    /**
     * 连接超时时间（秒）
     */
    @JsonProperty("connectionTimeout")
    private Integer connectionTimeout;

    /**
     * 查询超时时间（秒）
     */
    @JsonProperty("queryTimeout")
    private Integer queryTimeout;
}
