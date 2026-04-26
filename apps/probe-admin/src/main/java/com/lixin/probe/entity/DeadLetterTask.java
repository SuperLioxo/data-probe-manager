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
@TableName("dead_letter_task")
public class DeadLetterTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long originalTaskId;

    private String taskName;

    private String sourceProbeKey;

    private String sourceTableName;

    /** DATABASE, MINIO, API */
    private String targetType;

    /** JSON: target connection config */
    private String targetConfig;

    /** FULL, INCREMENTAL, CHANGE_BASED */
    private String syncMode;

    private String failureReason;

    private String failureStack;

    @Builder.Default
    private Integer retryCount = 0;

    @Builder.Default
    private Integer maxRetries = 3;

    /** PENDING, RETRYING, EXHAUSTED, RESOLVED */
    private String status;

    private LocalDateTime nextRetryTime;

    private LocalDateTime createTime;

    private LocalDateTime lastRetryTime;
}
