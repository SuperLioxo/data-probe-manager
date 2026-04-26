package com.lixin.probe.engine;

import com.lixin.probe.entity.MetricData;
import org.springframework.context.ApplicationEvent;

/**
 * 监控数据事件
 */
public class MetricDataEvent extends ApplicationEvent {

    private final MetricData metricData;

    public MetricDataEvent(Object source, MetricData metricData) {
        super(source);
        this.metricData = metricData;
    }

    public MetricData getMetricData() {
        return metricData;
    }
}
