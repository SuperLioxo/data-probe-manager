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
 * 探针实体类
 *
 * <p>对应数据库表probe，存储探针的基本信息、配置和状态。</p>
 *
 * <p>探针类型说明：
 * <ul>
 *   <li><b>SYSTEM</b> - 系统探针，用于系统级监控</li>
 *   <li><b>FILE</b> - 文件探针，用于文件系统监控</li>
 *   <li><b>DATABASE</b> - 数据库探针，用于数据库性能监控</li>
 *   <li><b>UNIFIED</b> - 统一探针，支持多种监控类型</li>
 * </ul></p>
 *
 * <p>探针状态说明：
 * <h3>连接状态 (status字段)：</h3>
 * <ul>
 *   <li><b>online</b> - 在线，探针与Admin的WebSocket连接正常</li>
 *   <li><b>offline</b> - 离线，探针失去连接或心跳超时</li>
 *   <li><b>error</b> - 错误状态</li>
 *   <li><b>disabled</b> - 已禁用，探针被管理员禁用</li>
 * </ul>
 *
 * <h3>运行状态 (runningStatus字段)：</h3>
 * <ul>
 *   <li><b>running</b> - 运行中，探针正在采集数据</li>
 *   <li><b>stopped</b> - 已停止，探针未在采集数据</li>
 * </ul>
 * </p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 * @see ProbeService
 * @see ProbeController
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("probe")
public class Probe implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 探针ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 探针唯一标识
     */
    @Size(max = 100, message = "探针标识长度不能超过100个字符")
    private String probeKey;

    /**
     * 探针名称
     */
    @NotBlank(message = "探针名称不能为空")
    @Size(min = 2, max = 100, message = "探针名称长度必须在2-100之间")
    private String name;

    /**
     * 探针类型
     * 支持的类型：SYSTEM（系统监控）、FILE（文件监控）、DATABASE（数据库监控）
     */
    @Pattern(regexp = "^(SYSTEM|FILE|DATABASE)$", message = "探针类型必须是SYSTEM、FILE或DATABASE")
    @TableField(value = "\"type\"")
    private String type;

    /**
     * 探针连接状态
     * 表示Agent与Admin的WebSocket连接状态
     */
    @Pattern(regexp = "^(online|offline|error|disabled)$", message = "连接状态必须是online、offline、error或disabled")
    private String status;

    /**
     * 探针运行状态
     * 表示探针是否在采集数据（独立于连接状态）
     */
    @Pattern(regexp = "^(running|stopped)$", message = "运行状态必须是running或stopped")
    private String runningStatus;

    /**
     * Agent 编码，显式关联所属 Agent
     */
    private String agentCode;

    /**
     * 运行状态最后更新时间
     */
    @TableField(exist = false)
    private LocalDateTime runningStatusUpdatedTime;

    /**
     * 探针所在主机IP（可选，留空则使用默认值127.0.0.1）
     */
    @Pattern(regexp = "^$|^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$", message = "IP地址格式不正确")
    private String hostIp;

    /**
     * 探针所在主机端口
     */
    @Min(value = 1, message = "端口必须大于0")
    @Max(value = 65535, message = "端口必须小于65536")
    private Integer port;

    /**
     * 探针软件版本号（如 1.0.0）
     */
    private String version;

    /**
     * 配置信息（JSON格式）
     */
    @Size(max = 5000, message = "配置信息长度不能超过5000")
    private String config;

    /**
     * 探针描述（暂存于虚拟字段，Phase 2 迁入 config JSON）
     */
    @TableField(exist = false)
    private String description;

    /**
     * 采集间隔（秒）
     */
    @Min(value = 1, message = "采集间隔必须大于等于1秒")
    @Max(value = 3600, message = "采集间隔必须小于等于3600秒")
    private Integer collectInterval;

    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeat;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
