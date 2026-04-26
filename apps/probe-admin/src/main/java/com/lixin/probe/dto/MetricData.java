package com.lixin.probe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 指标数据传输对象
 *
 * <p>用于封装探针采集的指标数据，支持多种数据类型和标签。</p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 探针标识
     */
    private String probeKey;

    /**
     * 指标名称（如：cpu.usage, memory.used）
     */
    private String metricName;

    /**
     * 指标值
     */
    private double value;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 标签（维度信息，如：host=server1, region=cn-north）
     */
    private Map<String, String> tags;

    /**
     * 指标类型（GAUGE, COUNTER, HISTOGRAM等）
     */
    private String metricType;

    /**
     * 数据单位（%, MB, GB, ms等）
     */
    private String unit;
}
