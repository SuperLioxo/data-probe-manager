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
@TableName("constraint_info")
public class ConstraintInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String probeKey;

    private String databaseName;

    private String tableName;

    private String constraintName;

    private String constraintType;

    private String columnName;

    private String checkClause;

    private String defaultValue;

    private LocalDateTime createTime;
}
