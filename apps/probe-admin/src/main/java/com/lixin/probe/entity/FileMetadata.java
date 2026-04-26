package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件元数据实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("file_metadata")
public class FileMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long probeId;

    private String probeKey;

    // 文件基本信息
    private String fileName;

    private String filePath;

    private Long fileSize;

    private String fileExtension;

    private String fileMd5;

    private String fileType;  // FILE, DIRECTORY

    // 层级关系
    private String parentPath;

    private Integer depth;

    // 时间信息
    private Long lastModified;  // Unix毫秒时间戳

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // 状态（逻辑删除标记）
    @TableField("is_deleted")
    private Boolean isDeleted;  // FALSE-未删除，TRUE-已删除
}
