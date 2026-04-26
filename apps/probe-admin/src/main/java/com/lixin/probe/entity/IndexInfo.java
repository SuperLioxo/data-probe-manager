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
@TableName("index_info")
public class IndexInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String probeKey;

    private String databaseName;

    private String tableName;

    private String indexName;

    private String columnNames;

    private String indexType;

    private Boolean isUnique;

    private Boolean isPrimary;

    private LocalDateTime createTime;
}
