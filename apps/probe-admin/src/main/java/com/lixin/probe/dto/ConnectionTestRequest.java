package com.lixin.probe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库连接测试请求DTO
 *
 * @author Claude Code
 * @date 2026-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "测试数据库连接请求")
public class ConnectionTestRequest {

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
}
