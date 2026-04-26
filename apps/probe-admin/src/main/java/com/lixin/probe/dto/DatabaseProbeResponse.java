package com.lixin.probe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据库探针响应DTO
 *
 * @author Claude Code
 * @date 2026-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据库探针响应")
public class DatabaseProbeResponse {

    @Schema(description = "探针ID")
    private Long id;

    @Schema(description = "探针标识")
    private String probeKey;

    @Schema(description = "探针名称")
    private String name;

    @Schema(description = "探针类型")
    private String type;

    @Schema(description = "探针状态")
    private String status;

    @Schema(description = "Agent主机IP")
    private String hostIp;

    @Schema(description = "Agent端口")
    private Integer port;

    @Schema(description = "版本号")
    private String version;

    @Schema(description = "数据库类型")
    private String databaseType;

    @Schema(description = "数据库主机")
    private String databaseHost;

    @Schema(description = "数据库端口")
    private Integer databasePort;

    @Schema(description = "数据库名称")
    private String databaseName;

    @Schema(description = "用户名")
    private String username;

    /**
     * 注意：响应中不返回密码
     */
    @Schema(description = "密码（不返回）")
    private String password;

    @Schema(description = "监控的schema列表")
    private String schemas;

    @Schema(description = "采集间隔（秒）")
    private Integer collectInterval;

    @Schema(description = "最后采集时间")
    private LocalDateTime lastCollectTime;

    @Schema(description = "最后心跳时间")
    private LocalDateTime lastHeartbeat;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
