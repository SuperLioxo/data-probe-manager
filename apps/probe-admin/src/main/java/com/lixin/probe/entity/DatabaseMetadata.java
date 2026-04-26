package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库元数据实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("database_metadata")
public class DatabaseMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 探针KEY
     */
    private String probeKey;

    /**
     * 数据库类型 (MySQL, PostgreSQL, Oracle, etc.)
     */
    private String databaseType;

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * 数据库版本
     */
    @TableField("\"version\"")
    private String version;

    /**
     * 字符集
     */
    private String charset;

    /**
     * 排序规则
     */
    @TableField("\"collation\"")
    private String collation;

    /**
     * 连接URL
     */
    @TableField("\"url\"")
    private String url;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
