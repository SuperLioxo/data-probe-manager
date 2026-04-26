package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库表信息实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("table_info")
public class TableInfo implements Serializable {

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
     * 存储引擎
     */
    private String engine;

    /**
     * 行数
     */
    private Long rowCount;

    /**
     * 数据大小（字节）
     */
    private Long dataSize;

    /**
     * 索引大小（字节）
     */
    private Long indexSize;

    /**
     * 总大小（字节）
     */
    private Long totalSize;

    /**
     * 创建时间
     */
    private String createTimeStr;

    /**
     * 更新时间
     */
    private String updateTimeStr;

    /**
     * 数据库记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 数据库记录更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
