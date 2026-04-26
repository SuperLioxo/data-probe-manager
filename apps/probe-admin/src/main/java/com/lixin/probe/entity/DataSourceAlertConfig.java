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
@TableName("datasource_alert_config")
public class DataSourceAlertConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configName;
    private String probeKey;
    private Integer checkIntervalSeconds;
    private Integer timeoutThresholdMs;
    private Integer consecutiveFailures;
    /** WARNING, CRITICAL */
    private String alertLevel;
    private String notifyChannels;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
