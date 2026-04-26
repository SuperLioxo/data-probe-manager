package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件扫描历史记录实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("file_scan_history")
public class FileScanHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long probeId;

    private String probeKey;

    // 扫描时间信息
    private LocalDateTime scanStartTime;

    private LocalDateTime scanEndTime;

    private Integer scanDuration;

    // 扫描结果统计
    private Long fileCount;

    private Long directoryCount;

    private Long totalSize;

    private Integer newFileCount;

    private Integer modifiedFileCount;

    private Integer deletedFileCount;

    // 扫描状态
    private String scanStatus;

    private String errorMessage;

    private LocalDateTime createTime;
}
