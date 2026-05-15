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
@TableName("change_alert_config")
public class ChangeAlertConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String alertName;
    private String probeKey;
    private String tableName;
    private String changeTypes;
    private Long thresholdRows;
    /** WARNING, CRITICAL */
    private String alertLevel;
    /** LOG, WEBSOCKET */
    private String notifyChannels;
    private Boolean enabled;
    private LocalDateTime createTime;
}
