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
@TableName("alert_channel")
public class AlertChannel implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** WEBHOOK, DINGTALK, WECOM, EMAIL */
    private String channelType;

    /** JSON config — varies by channel type */
    private String config;

    private Boolean enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
