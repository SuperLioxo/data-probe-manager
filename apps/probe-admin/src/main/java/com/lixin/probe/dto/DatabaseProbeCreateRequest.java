package com.lixin.probe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库探针创建请求DTO
 *
 * @author Claude Code
 * @date 2026-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建数据库探针请求")
public class DatabaseProbeCreateRequest {

    /**
     * 探针唯一标识
     */
    @Schema(description = "探针标识", example = "AGENT-database-test-001", required = true)
    @NotBlank(message = "探针标识不能为空")
    @Size(min = 3, max = 64, message = "探针标识长度必须在3-64之间")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "探针标识只能包含字母、数字、下划线和连字符")
    private String probeKey;

    /**
     * 探针名称
     */
    @Schema(description = "探针名称", example = "生产数据库监控", required = true)
    @NotBlank(message = "探针名称不能为空")
    @Size(min = 2, max = 128, message = "探针名称长度必须在2-128之间")
    private String name;

    /**
     * Agent主机IP
     */
    @Schema(description = "Agent主机IP地址", example = "127.0.0.1")
    @Pattern(regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^$", message = "IP地址格式不正确")
    private String hostIp;

    /**
     * Agent端口
     */
    @Schema(description = "Agent端口", example = "58081")
    @Min(value = 1, message = "端口必须大于0")
    @Max(value = 65535, message = "端口必须小于65536")
    private Integer port;

    /**
     * 数据库类型
     */
    @Schema(description = "数据库类型", example = "PostgreSQL", required = true,
            allowableValues = {"MySQL", "PostgreSQL", "Oracle", "SQL Server"})
    @NotBlank(message = "数据库类型不能为空")
    @Pattern(regexp = "^(MySQL|PostgreSQL|Oracle|SQL Server)$", message = "数据库类型必须是MySQL、PostgreSQL、Oracle或SQL Server")
    private String databaseType;

    /**
     * 数据库主机
     */
    @Schema(description = "数据库主机", example = "localhost", required = true)
    @NotBlank(message = "数据库主机不能为空")
    @Size(max = 128, message = "数据库主机长度不能超过128")
    private String databaseHost;

    /**
     * 数据库端口
     */
    @Schema(description = "数据库端口", example = "5433", required = true)
    @NotNull(message = "数据库端口不能为空")
    @Min(value = 1, message = "数据库端口必须大于0")
    @Max(value = 65535, message = "数据库端口必须小于65536")
    private Integer databasePort;

    /**
     * 数据库名称
     */
    @Schema(description = "数据库名称", example = "probe_db", required = true)
    @NotBlank(message = "数据库名称不能为空")
    @Size(max = 128, message = "数据库名称长度不能超过128")
    private String databaseName;

    /**
     * 数据库用户名
     */
    @Schema(description = "数据库用户名", example = "probe_user", required = true)
    @NotBlank(message = "用户名不能为空")
    @Size(max = 128, message = "用户名长度不能超过128")
    private String username;

    /**
     * 数据库密码
     */
    @Schema(description = "数据库密码", example = "probe_pass", required = true)
    @NotBlank(message = "密码不能为空")
    @Size(max = 500, message = "密码长度不能超过500")
    private String password;

    /**
     * 监控的schema列表（逗号分隔）
     */
    @Schema(description = "监控的schema列表", example = "public,information_schema")
    @Size(max = 500, message = "schema配置长度不能超过500")
    private String schemas;

    /**
     * 采集间隔（秒）
     */
    @Schema(description = "采集间隔（秒）", example = "60")
    @Min(value = 1, message = "采集间隔必须大于0")
    @Max(value = 86400, message = "采集间隔不能超过86400秒（24小时）")
    private Integer collectInterval;
}
