package com.lixin.probe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 探针指标摘要
 * 用于前端显示系统探针的核心指标
 *
 * @author Claude Code
 * @date 2026-03-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProbeMetricsSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * CPU使用率 (百分比)
     * 范围: 0-100
     */
    private Double cpuUsage;

    /**
     * 内存使用率 (百分比)
     * 范围: 0-100
     */
    private Double memoryUsage;

    /**
     * 已用内存 (字节)
     */
    private Long memoryUsed;

    /**
     * 总内存 (字节)
     */
    private Long memoryTotal;

    /**
     * 可用内存 (字节)
     */
    private Long memoryAvailable;

    /**
     * 磁盘使用率 (百分比)
     * 根据挂载点标记，如: root: 75.5
     */
    private Double diskUsage;

    /**
     * 网络接收速率 (字节/秒)
     */
    private Double networkRxRate;

    /**
     * 网络发送速率 (字节/秒)
     */
    private Double networkTxRate;

    /**
     * CPU负载1分钟平均值
     */
    private Double cpuLoad1min;

    /**
     * CPU负载5分钟平均值
     */
    private Double cpuLoad5min;

    /**
     * CPU负载15分钟平均值
     */
    private Double cpuLoad15min;

    /**
     * CPU核心数
     */
    private Integer cpuCores;

    /**
     * 磁盘已用空间（字节）
     */
    private Long diskUsed;

    /**
     * 磁盘总空间（字节）
     */
    private Long diskTotal;

    /**
     * 网络累计接收字节数
     */
    private Long networkRxBytes;

    /**
     * 网络累计发送字节数
     */
    private Long networkTxBytes;

    /**
     * 网络接收错误数
     */
    private Long networkRxErrors;

    /**
     * 网络发送错误数
     */
    private Long networkTxErrors;

    // ==================== JVM 指标 ====================

    /**
     * JVM堆内存已用 (MB)
     */
    private Double jvmHeapUsed;

    /**
     * JVM堆内存最大值 (MB)
     */
    private Double jvmHeapMax;

    /**
     * JVM堆内存使用率 (百分比)
     */
    private Double jvmHeapUsage;

    /**
     * JVM线程数
     */
    private Integer jvmThreadCount;

    /**
     * JVM峰值线程数
     */
    private Integer jvmThreadPeak;

    /**
     * JVM已加载类数
     */
    private Integer jvmClassLoaded;

    /**
     * JVM总内存 (MB)
     */
    private Double jvmTotalMemory;

    /**
     * JVM空闲内存 (MB)
     */
    private Double jvmFreeMemory;

    // ==================== OS 指标 ====================

    /**
     * 系统进程数
     */
    private Integer osProcessCount;

    /**
     * 系统线程总数
     */
    private Integer osThreadCount;

    /**
     * 系统运行时间 (秒)
     */
    private Long osUptimeSeconds;

    // ==================== 进程指标 ====================

    /**
     * 进程CPU使用率 (百分比)
     */
    private Double processCpuUsage;

    /**
     * 进程常驻内存 (MB)
     */
    private Double processMemoryResident;

    /**
     * JVM运行时间 (秒)
     */
    private Long processJvmUptime;

    /**
     * 进程ID
     */
    private Long processId;

    /**
     * 数据采集时间
     */
    private LocalDateTime collectTime;

    /**
     * 快速创建CPU和内存摘要
     */
    public static ProbeMetricsSummary of(Double cpuUsage, Double memoryUsage, Long memoryUsed) {
        return ProbeMetricsSummary.builder()
                .cpuUsage(cpuUsage)
                .memoryUsage(memoryUsage)
                .memoryUsed(memoryUsed)
                .collectTime(LocalDateTime.now())
                .build();
    }
}
