package com.lixin.probe.agent.pojo.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据库实例响应对象
 *
 * @author Claude Code
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseInstanceResponse {

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 数据库类型
     */
    private String databaseType;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * 用户名
     */
    private String username;

    /**
     * Schema列表
     */
    private List<String> schemas;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 描述
     */
    private String description;

    /**
     * 连接超时（秒）
     */
    private Integer connectionTimeout;

    /**
     * 查询超时（秒）
     */
    private Integer queryTimeout;

    /**
     * 状态
     */
    private String status;

    /**
     * 最后连接时间
     */
    private Long lastConnectTime;
}
