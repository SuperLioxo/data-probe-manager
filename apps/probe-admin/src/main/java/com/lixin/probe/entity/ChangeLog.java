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
@TableName("change_log")
public class ChangeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String probeKey;

    private String databaseName;

    private String tableName;

    /** ROW_INSERT, ROW_UPDATE, ROW_DELETE, SCHEMA_CHANGE, COUNT_CHANGE, SIZE_CHANGE */
    private String changeType;

    /** JSON: 变化详情 */
    private String changeDetail;

    private Long affectedRows;

    private Long snapshotBeforeId;

    private Long snapshotAfterId;

    private LocalDateTime detectedTime;
}
