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
@TableName("webhook_config")
public class WebhookConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String webhookName;
    private String webhookKey;
    /** JSON: 期望的数据结构描述 */
    private String schemaDescription;
    private String targetProbeKey;
    private String targetTableName;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime lastReceivedTime;
    private Long receiveCount;
}
