package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("foreign_key_info")
public class ForeignKeyInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String probeKey;

    private String databaseName;

    private String tableName;

    private String constraintName;

    private String columnName;

    private String refTable;

    private String refColumn;

    private String updateRule;

    private String deleteRule;

    private LocalDateTime createTime;
}
