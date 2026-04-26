package com.lixin.probe.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库连接DTO
 *
 * @author Claude Code
 * @date 2026-03-26
 */
@Data
@Builder
public class DatabaseConnectionDTO {
    private Long id;
    private String name;
    private String databaseType;
    private String databaseHost;
    private Integer databasePort;
    private String databaseName;
    private String username;
    private String password; // 仅在创建/更新时返回，列表查询时为null
    private String schemas;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
