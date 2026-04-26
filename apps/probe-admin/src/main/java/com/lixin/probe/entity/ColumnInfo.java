package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库表字段信息实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("column_info")
public class ColumnInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 探针KEY
     */
    private String probeKey;

    /**
     * 数据库名称（用于区分同一探针下的不同数据库实例）
     */
    private String databaseName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 字段名
     */
    private String columnName;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 类型详情
     */
    private String columnType;

    /**
     * 是否允许NULL
     */
    private Boolean isNullable;

    /**
     * 键类型 (PRI, UNI, MUL, etc.)
     */
    private String keyType;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 额外信息 (auto_increment, etc.)
     */
    private String extra;

    /**
     * 字段注释
     */
    private String comment;

    /**
     * 数据库记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
