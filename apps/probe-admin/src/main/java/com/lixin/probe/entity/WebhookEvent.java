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
@TableName("webhook_event")
public class WebhookEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String webhookKey;
    private String sourceIp;
    private String payload;
    private String payloadChecksum;
    private String status;
    /** JSON: 处理结果 */
    private String processResult;
    private LocalDateTime receivedTime;
    private LocalDateTime processedTime;
}
