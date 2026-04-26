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
@TableName("datasource_alert_record")
public class DataSourceAlertRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long alertConfigId;
    private String probeKey;
    private String datasourceName;
    /** OFFLINE, TIMEOUT, AUTH_FAIL */
    private String status;
    private Long latencyMs;
    private String errorMessage;
    private Integer consecutiveCount;
    private String alertLevel;
    private String notifyChannel;
    /** PENDING, RESOLVED */
    private String alertStatus;
    private LocalDateTime createdTime;
    private LocalDateTime resolvedTime;
}
