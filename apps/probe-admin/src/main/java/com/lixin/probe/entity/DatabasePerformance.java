package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库性能监控实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("database_performance")
public class DatabasePerformance implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 探针ID
     */
    private Long probeId;

    /**
     * 探针KEY
     */
    private String probeKey;

    /**
     * 数据库类型 (MySQL, PostgreSQL, Oracle, etc.)
     */
    private String databaseType;

    /**
     * 数据库主机
     */
    private String host;

    /**
     * 数据库端口
     */
    private Integer port;

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * 连接数
     */
    private Integer connectionCount;

    /**
     * 活跃连接数
     */
    private Integer activeConnections;

    /**
     * 最大连接数
     */
    private Integer maxConnections;

    /**
     * 连接使用率 (%)
     */
    private Double connectionUsage;

    /**
     * 平均查询耗时 (ms)
     */
    private Long avgQueryTime;

    /**
     * 慢查询数量
     */
    private Long slowQueryCount;

    /**
     * QPS (每秒查询数)
     */
    private Double qps;

    /**
     * TPS (每秒事务数)
     */
    private Double tps;

    /**
     * 缓存命中率 (%)
     */
    private Double cacheHitRate;

    /**
     * 数据采集时间戳
     */
    private Long timestamp;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
