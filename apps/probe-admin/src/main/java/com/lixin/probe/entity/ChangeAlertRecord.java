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
@TableName("change_alert_record")
public class ChangeAlertRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long alertConfigId;
    private String probeKey;
    private String tableName;
    private String changeType;
    private String changeDetail;
    private Long affectedRows;
    private String alertLevel;
    private String notifyChannel;
    /** PENDING, RESOLVED, SILENCED */
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime resolvedTime;
}
