package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("alert")
public class Alert implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 探针ID
     */
    private Long probeId;

    /**
     * 探针名称
     */
    private String probeName;

    /**
     * 告警类型
     */
    private String alertType;

    /**
     * 告警规则ID
     */
    private Long ruleId;

    /**
     * 告警消息
     */
    private String message;

    /**
     * 严重级别: CRITICAL, MAJOR, MINOR, INFO
     */
    private String severity;

    /**
     * 告警状态: OPEN, RESOLVED, CLOSED
     */
    private String status;

    /**
     * 告警标题
     */
    private String title;

    /**
     * 告警描述
     */
    private String description;

    /**
     * 触发时间
     */
    private LocalDateTime triggeredAt;

    /**
     * 确认时间
     */
    private LocalDateTime acknowledgedAt;

    /**
     * 解决时间
     */
    private LocalDateTime resolvedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 解决方案
     */
    private String resolution;
}
