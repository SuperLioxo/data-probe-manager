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
@TableName("sync_task")
public class SyncTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskName;

    private String sourceProbeKey;

    private String sourceTableName;

    /** DATABASE, MINIO, API */
    private String targetType;

    /** JSON: 目标连接配置 */
    private String targetConfig;

    /** FULL, INCREMENTAL, CHANGE_BASED */
    private String syncMode;

    private String cronExpression;

    /** INSERT, UPSERT, SKIP */
    private String conflictStrategy;

    private String lastSyncPosition;

    private LocalDateTime lastSyncTime;

    /** RUNNING, SUCCESS, FAILED */
    private String lastSyncStatus;

    private LocalDateTime nextSyncTime;

    private Boolean enabled;

    private Boolean qualityCheckEnabled;

    private Boolean realtimeSyncEnabled;

    private String realtimeSyncConfig;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
