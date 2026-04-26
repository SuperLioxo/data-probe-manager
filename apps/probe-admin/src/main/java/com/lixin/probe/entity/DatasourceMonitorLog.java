package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("datasource_monitor_log")
public class DatasourceMonitorLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String probeKey;
    /** CONNECTION_POOL, QUERY_PERFORMANCE, STORAGE, JVM_MEMORY, JVM_THREADS */
    private String metricType;
    private Double metricValue;
    private String metricUnit;
    private String extraInfo;
    private LocalDateTime collectedTime;
}
