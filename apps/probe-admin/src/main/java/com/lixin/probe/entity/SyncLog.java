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
@TableName("sync_log")
public class SyncLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String syncMode;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** RUNNING, SUCCESS, FAILED */
    private String status;

    private Long rowsProcessed;

    private Long rowsFailed;

    private String errorMessage;

    private String startPosition;

    private String endPosition;
}
