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
 * 数据库探针实体类
 *
 * @author Claude Code
 * @date 2026-03-26
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("database_probe")
public class DatabaseProbe implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "探针标识不能为空")
    @Size(min = 3, max = 64, message = "探针标识长度必须在3-64之间")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "探针标识只能包含字母、数字、下划线和连字符")
    private String probeKey;

    @NotBlank(message = "探针名称不能为空")
    @Size(min = 2, max = 128, message = "探针名称长度必须在2-128之间")
    private String name;

    @NotBlank(message = "探针类型不能为空")
    @Pattern(regexp = "^DATABASE$", message = "数据库探针类型必须为DATABASE")
    @TableField(value = "\"type\"")
    private String type;

    @Pattern(regexp = "^(online|offline|error|disabled)$", message = "探针状态必须是online、offline、error或disabled")
    private String status;

    @Pattern(regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^$", message = "IP地址格式不正确")
    private String hostIp;

    @Min(value = 1, message = "端口必须大于0")
    @Max(value = 65535, message = "端口必须小于65536")
    private Integer port;

    @Size(max = 32, message = "版本号长度不能超过32")
    @TableField(value = "\"version\"")
    private String version;

    // Database connection configuration
    @NotBlank(message = "数据库类型不能为空")
    @Pattern(regexp = "^(MySQL|PostgreSQL|Oracle|SQL Server)$", message = "数据库类型必须是MySQL、PostgreSQL、Oracle或SQL Server")
    private String databaseType;

    @NotBlank(message = "数据库主机不能为空")
    @Size(max = 128, message = "数据库主机长度不能超过128")
    private String databaseHost;

    @NotNull(message = "数据库端口不能为空")
    @Min(value = 1, message = "数据库端口必须大于0")
    @Max(value = 65535, message = "数据库端口必须小于65536")
    private Integer databasePort;

    @NotBlank(message = "数据库名称不能为空")
    @Size(max = 128, message = "数据库名称长度不能超过128")
    private String databaseName;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 128, message = "用户名长度不能超过128")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 500, message = "密码长度不能超过500")
    private String password;

    @Size(max = 500, message = "schema配置长度不能超过500")
    private String schemas;

    @Min(value = 1, message = "采集间隔必须大于0")
    @Max(value = 86400, message = "采集间隔不能超过86400秒（24小时）")
    private Integer collectInterval;

    // Connection pool management
    private Long currentConnectionId;

    private String connectionPool;

    // Statistics
    private LocalDateTime lastCollectTime;

    private LocalDateTime lastHeartbeat;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
