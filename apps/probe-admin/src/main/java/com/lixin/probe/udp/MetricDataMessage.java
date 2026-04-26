package com.lixin.probe.udp;

import lombok.Data;

/**
 * UDP数据消息
 */
@Data
public class MetricDataMessage {
    private String probeKey;
    private String metricName;
    private Double metricValue;
    private Long timestamp;
    private String senderAddress;
}
