package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件探针实体类
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (添加验证注解)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("file_probe")
public class FileProbe implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "探针标识不能为空")
    @Size(min = 3, max = 50, message = "探针标识长度必须在3-50之间")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "探针标识只能包含字母、数字、下划线和连字符")
    private String probeKey;

    @NotBlank(message = "探针名称不能为空")
    @Size(min = 2, max = 100, message = "探针名称长度必须在2-100之间")
    private String name;

    @NotBlank(message = "探针类型不能为空")
    @Pattern(regexp = "^FILE$", message = "文件探针类型必须为FILE")
    @TableField(value = "\"type\"")
    private String type;

    @Pattern(regexp = "^(online|offline|error|disabled)$", message = "探针状态必须是online、offline、error或disabled")
    private String status;

    @Pattern(regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^$", message = "IP地址格式不正确")
    private String hostIp;

    @Min(value = 1, message = "端口必须大于0")
    @Max(value = 65535, message = "端口必须小于65536")
    private Integer port;

    @Size(max = 20, message = "版本号长度不能超过20")
    @TableField(value = "\"version\"")
    private String version;

    // 文件探针配置
    @NotBlank(message = "扫描路径不能为空")
    @Size(max = 500, message = "扫描路径长度不能超过500")
    private String scanPath;

    @Size(max = 200, message = "文件扩展名列表长度不能超过200")
    private String fileExtensions;

    @Size(max = 500, message = "忽略路径列表长度不能超过500")
    private String ignorePaths;

    @Min(value = 1, message = "扫描间隔必须大于0")
    @Max(value = 86400, message = "扫描间隔不能超过86400秒（24小时）")
    private Integer scanInterval;

    @Min(value = 1, message = "最大深度必须大于0")
    @Max(value = 100, message = "最大深度不能超过100")
    private Integer maxDepth;

    // 统计信息
    private Long totalFileCount;

    private Long totalDirectoryCount;

    private Long totalSize;

    private LocalDateTime lastScanTime;

    private LocalDateTime lastHeartbeat;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
