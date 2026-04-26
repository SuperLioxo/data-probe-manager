package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库连接配置实体
 *
 * @author Claude Code
 * @date 2026-03-26
 */
@Data
@TableName("database_connection")
public class DatabaseConnection {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 连接显示名称（如"生产库"、"测试库"）
     */
    private String name;

    /**
     * 数据库类型（MySQL、PostgreSQL、Oracle、SQL Server）
     */
    private String databaseType;

    /**
     * 数据库主机
     */
    private String databaseHost;

    /**
     * 数据库端口
     */
    private Integer databasePort;

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码（加密存储）
     */
    private String password;

    /**
     * Schema列表（逗号分隔）
     */
    private String schemas;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
