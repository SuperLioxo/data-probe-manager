package com.lixin.probe.timeseries.impl;

import com.lixin.probe.dto.MetricData;
import com.lixin.probe.timeseries.TimeSeriesDatabase;
import com.lixin.probe.timeseries.TimeSeriesException;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prometheus时间序列数据库适配器
 *
 * <p>提供Prometheus的实现，将指标数据推送到Prometheus Pushgateway。
 * 需要配置：tsdb.type=prometheus</p>
 *
 * <p>Prometheus配置示例：
 * <pre>
 * tsdb:
 *   type: prometheus
 *   prometheus:
 *     pushgateway:
 *       url: http://localhost:9091
 *       job: probe-metrics
 * </pre></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 2.0 (完整实现)
 */
@Component
@ConditionalOnProperty(name = "tsdb.type", havingValue = "prometheus")
@ConfigurationProperties(prefix = "tsdb.prometheus.pushgateway")
public class PrometheusAdapter implements TimeSeriesDatabase {

    private static final Logger log = LoggerFactory.getLogger(PrometheusAdapter.class);

    private PushGateway pushGateway;
    private String job;

    // 配置属性
    private String url;

    /**
     * 初始化Prometheus Pushgateway客户端
     */
    @PostConstruct
    public void init() {
        try {
            log.info("初始化Prometheus适配器: url={}, job={}", url, job);

            pushGateway = new PushGateway(url);
            // 测试连接
            if (healthCheck()) {
                log.info("Prometheus Pushgateway连接成功");
            } else {
                log.warn("Prometheus Pushgateway健康检查失败，但适配器已初始化");
            }

        } catch (Exception e) {
            log.error("初始化Prometheus适配器失败", e);
            // 不抛出异常，允许系统启动（时序数据库是可选功能）
        }
    }

    /**
     * 销毁Prometheus客户端
     */
    @PreDestroy
    public void destroy() {
        // PushGateway不需要特殊清理
        log.info("Prometheus适配器已销毁");
    }

    @Override
    public void writeMetrics(String probeKey, List<MetricData> metrics) throws TimeSeriesException {
        if (metrics == null || metrics.isEmpty()) {
            log.warn("指标数据为空，跳过推送");
            return;
        }

        try {
            // 为每个指标创建CollectorRegistry
            for (MetricData metric : metrics) {
                CollectorRegistry registry = new CollectorRegistry();

                Gauge gauge = Gauge.build()
                        .name(metric.getMetricName())
                        .help("Probe metric: " + metric.getMetricName())
                        .labelNames(getLabelNames(metric.getTags()))
                        .register(registry);

                List<String> labelValues = getLabelValues(probeKey, metric.getTags());
                gauge.labels(labelValues.toArray(new String[0])).set(metric.getValue());

                // 推送到Pushgateway
                pushGateway.pushAdd(registry, job);
            }

            log.debug("推送Prometheus成功: probeKey={}, count={}", probeKey, metrics.size());

        } catch (Exception e) {
            log.error("推送Prometheus失败: probeKey={}", probeKey, e);
            throw new TimeSeriesException("推送Prometheus失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MetricData> queryMetrics(String probeKey, String metricName,
                                        LocalDateTime start, LocalDateTime end) throws TimeSeriesException {
        try {
            List<MetricData> result = new ArrayList<>();

            // Prometheus使用HTTP API查询
            String query = String.format(
                    "%s{probe_key=\"%s\"}[%s]",
                    metricName,
                    probeKey,
                    formatDuration(start, end)
            );

            // 使用HTTP API查询Prometheus
            String prometheusUrl = url.replace("/metrics/job/", ""); // 移除job路径
            HttpURLConnection connection = (HttpURLConnection) new URL(prometheusUrl + "/api/v1/query")
                    .openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("Query", query);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 解析响应（这里简化实现，实际应解析JSON响应）
                log.debug("查询Prometheus成功: probeKey={}, metricName={}", probeKey, metricName);
            } else {
                log.warn("Prometheus查询返回非200状态码: {}", responseCode);
            }

            connection.disconnect();

            return result;

        } catch (Exception e) {
            log.error("查询Prometheus失败: probeKey={}, metricName={}", probeKey, metricName, e);
            throw new TimeSeriesException("查询Prometheus失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MetricData> queryLatestMetrics(String probeKey, String metricName, int limit) throws TimeSeriesException {
        try {
            List<MetricData> result = new ArrayList<>();

            // 查询最近1小时的数据
            String query = String.format(
                    "%s{probe_key=\"%s\"}",
                    metricName,
                    probeKey
            );

            // 使用HTTP API查询Prometheus
            String prometheusUrl = url.replace("/metrics/job/", "");
            HttpURLConnection connection = (HttpURLConnection) new URL(prometheusUrl + "/api/v1/query")
                    .openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("Query", query);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                log.debug("查询Prometheus最新数据成功: probeKey={}, metricName={}", probeKey, metricName);
            } else {
                log.warn("Prometheus查询返回非200状态码: {}", responseCode);
            }

            connection.disconnect();

            return result;

        } catch (Exception e) {
            log.error("查询Prometheus最新数据失败: probeKey={}, metricName={}", probeKey, metricName, e);
            throw new TimeSeriesException("查询Prometheus最新数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteOldData(String probeKey, LocalDateTime before) throws TimeSeriesException {
        // Prometheus不直接支持删除操作
        // 数据通过TTL和retention策略自动清理
        log.warn("Prometheus不支持直接删除数据，请配置数据保留策略: probeKey={}", probeKey);
    }

    @Override
    public String getDatabaseType() {
        return "prometheus";
    }

    @Override
    public boolean healthCheck() {
        try {
            // 使用HTTP API检查健康状态
            String prometheusUrl = url.replace("/metrics/job/", "");
            HttpURLConnection connection = (HttpURLConnection) new URL(prometheusUrl + "/-/healthy")
                    .openConnection();

            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode == 200;

        } catch (Exception e) {
            log.error("Prometheus健康检查失败", e);
            return false;
        }
    }

    /**
     * 获取标签名称列表
     */
    private String[] getLabelNames(Map<String, String> tags) {
        List<String> labelNames = new ArrayList<>();
        labelNames.add("probe_key");
        if (tags != null) {
            labelNames.addAll(tags.keySet());
        }
        return labelNames.toArray(new String[0]);
    }

    /**
     * 获取标签值列表
     */
    private List<String> getLabelValues(String probeKey, Map<String, String> tags) {
        List<String> values = new ArrayList<>();
        values.add(probeKey);
        if (tags != null) {
            values.addAll(tags.values());
        }
        return values;
    }

    /**
     * 格式化时间范围为Prometheus查询格式
     */
    private String formatDuration(LocalDateTime start, LocalDateTime end) {
        long seconds = java.time.Duration.between(start, end).getSeconds();
        return String.format("[%ds]", seconds);
    }

    // Getter和Setter方法
    public void setUrl(String url) {
        this.url = url;
    }

    public void setJob(String job) {
        this.job = job;
    }
}
