package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent实体
 * 记录已注册的Agent程序信息
 *
 * @author Claude Code
 * @since 2.0
 */
@Data
@TableName("agent")
public class Agent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Agent代码（如：AGENT）
     */
    private String agentCode;

    /**
     * Agent名称
     */
    private String agentName;

    /**
     * 主机IP
     */
    private String hostIp;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 状态（online/offline）
     */
    private String status;

    /**
     * 版本号
     */
    private String version;

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
