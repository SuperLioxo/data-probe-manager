package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安全事件实体
 * 记录系统安全相关的事件，用于审计和追溯
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("security_event")
public class SecurityEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 事件类型
     * 例如: UNAUTHORIZED_REGISTRATION_ATTEMPT, IP_NOT_WHITELISTED, RATE_LIMIT_EXCEEDED
     */
    private String eventType;

    /**
     * 探针标识
     */
    private String probeKey;

    /**
     * 来源IP地址
     */
    private String ipAddress;

    /**
     * 事件详情
     */
    private String eventDetails;

    /**
     * 严重程度
     * LOW, MEDIUM, HIGH, CRITICAL
     */
    private String severity;

    /**
     * 事件状态
     * PENDING, INVESTIGATING, RESOLVED, IGNORED
     */
    private String status;

    /**
     * 发生时间
     */
    private LocalDateTime eventTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 处理备注
     */
    private String notes;
}
