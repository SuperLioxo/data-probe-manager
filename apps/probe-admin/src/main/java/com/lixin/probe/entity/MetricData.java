package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 监控数据实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("metric_data")
public class MetricData implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long probeId;

    private String probeKey;

    private String metricName;

    private BigDecimal metricValue;

    private String unit;

    private String tags;

    @TableField("collect_time")
    private LocalDateTime timestamp;

    private LocalDateTime createTime;
}
