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
@TableName("data_snapshot")
public class DataSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String probeKey;

    private String databaseName;

    private String tableName;

    private Long rowCount;

    private Long dataSize;

    private Long indexSize;

    private String dataChecksum;

    private String maxUpdateTime;

    private LocalDateTime snapshotTime;
}
