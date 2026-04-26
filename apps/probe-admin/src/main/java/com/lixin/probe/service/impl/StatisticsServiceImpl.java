package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.entity.Alert;
import com.lixin.probe.entity.MetricData;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.mapper.AlertMapper;
import com.lixin.probe.mapper.MetricDataMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 统计Service实现
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StatisticsServiceImpl.class);

    @Autowired
    private ProbeMapper probeMapper;

    @Autowired
    private AlertMapper alertMapper;

    @Autowired
    private MetricDataMapper metricDataMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private static final String PROBE_STATS_KEY = "stats:probe";
    private static final String ALERT_STATS_KEY = "stats:alert";
    private static final long CACHE_TTL = 10; // 10分钟

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getProbeStatistics() {
        // 尝试从缓存获取
        if (redisTemplate != null) {
            Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(PROBE_STATS_KEY);
            if (cached != null) {
                return cached;
            }
        }

        Map<String, Object> stats = new HashMap<>();
        
        // 总探针数
        Long total = probeMapper.selectCount(null);
        
        // 在线探针数
        Long online = probeMapper.selectCount(
            new LambdaQueryWrapper<Probe>().eq(Probe::getStatus, "online")
        );

        // 离线探针数
        Long offline = probeMapper.selectCount(
            new LambdaQueryWrapper<Probe>().eq(Probe::getStatus, "offline")
        );

        stats.put("total", total);
        stats.put("online", online);
        stats.put("offline", offline);
        stats.put("onlineRate", total > 0 ? online * 100.0 / total : 0);

        // 缓存结果
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(PROBE_STATS_KEY, stats, CACHE_TTL, TimeUnit.MINUTES);
        }

        return stats;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAlertStatistics() {
        // 尝试从缓存获取
        if (redisTemplate != null) {
            Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(ALERT_STATS_KEY);
            if (cached != null) {
                return cached;
            }
        }

        Map<String, Object> stats = new HashMap<>();
        
        // 今日告警总数
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        Long todayTotal = alertMapper.selectCount(
            new LambdaQueryWrapper<Alert>().ge(Alert::getTriggeredAt, todayStart)
        );
        
        // 未解决告警数
        Long openCount = alertMapper.selectCount(
            new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, "OPEN")
        );
        
        // 已解决告警数
        Long resolvedCount = alertMapper.selectCount(
            new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, "RESOLVED")
        );

        // 按严重级别统计
        Long criticalCount = alertMapper.selectCount(
            new LambdaQueryWrapper<Alert>()
                .eq(Alert::getStatus, "OPEN")
                .eq(Alert::getSeverity, "CRITICAL")
        );
        
        Long majorCount = alertMapper.selectCount(
            new LambdaQueryWrapper<Alert>()
                .eq(Alert::getStatus, "OPEN")
                .eq(Alert::getSeverity, "MAJOR")
        );

        stats.put("todayTotal", todayTotal);
        stats.put("openCount", openCount);
        stats.put("resolvedCount", resolvedCount);
        stats.put("criticalCount", criticalCount);
        stats.put("majorCount", majorCount);

        // 缓存结果
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(ALERT_STATS_KEY, stats, CACHE_TTL, TimeUnit.MINUTES);
        }

        return stats;
    }

    @Override
    public Map<String, Object> getOverview(String timeRange) {
        Map<String, Object> overview = new HashMap<>();
        Map<String, Object> probeStats = getProbeStatistics();
        Map<String, Object> alertStats = getAlertStatistics();

        // 根据timeRange计算时间范围
        LocalDateTime startTime = calculateStartTime(timeRange);
        log.info("获取概览统计, timeRange={}, startTime={}", timeRange, startTime);

        // 1. 总数据量：查询指定时间范围内的数据记录数
        LambdaQueryWrapper<MetricData> totalWrapper = new LambdaQueryWrapper<>();
        totalWrapper.ge(MetricData::getTimestamp, startTime);
        Long totalDataPoints = metricDataMapper.selectCount(totalWrapper);

        // 2. 平均采集速率：计算最近1分钟内的数据采集速率（条/分钟）
        LambdaQueryWrapper<MetricData> rateWrapper = new LambdaQueryWrapper<>();
        rateWrapper.ge(MetricData::getTimestamp, LocalDateTime.now().minusMinutes(1));
        Long recentDataPoints = metricDataMapper.selectCount(rateWrapper);
        int avgDataRate = recentDataPoints.intValue(); // 每分钟的数据量

        overview.put("totalDataPoints", totalDataPoints);
        overview.put("avgDataRate", avgDataRate);
        overview.put("totalAlerts", alertStats.getOrDefault("todayTotal", 0));
        overview.put("abnormalProbes", probeStats.getOrDefault("offline", 0));

        return overview;
    }

    @Override
    public Map<String, Object> getProbeTrend(String timeRange, String metric) {
        log.info("获取探针趋势数据, timeRange={}, metric={}", timeRange, metric);

        Map<String, Object> trend = new HashMap<>();
        List<String> timestamps = new ArrayList<>();
        List<Double> avgValues = new ArrayList<>();
        List<Double> maxValues = new ArrayList<>();

        // 计算时间范围
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime;
        int points;

        switch (timeRange) {
            case "1h":
                startTime = endTime.minusHours(1);
                points = 12;
                break;
            case "6h":
                startTime = endTime.minusHours(6);
                points = 12;
                break;
            case "24h":
                startTime = endTime.minusHours(24);
                points = 24;
                break;
            case "7d":
                startTime = endTime.minusDays(7);
                points = 7;
                break;
            case "30d":
                startTime = endTime.minusDays(30);
                points = 30;
                break;
            default:
                startTime = endTime.minusHours(24);
                points = 24;
        }

        // 将前端传入的 metric 转换为实际的指标名称
        String metricName = convertMetricToName(metric);
        log.info("转换后的指标名称: {}", metricName);

        // 获取CPU核心数（用于计算百分比）
        Double cpuCores = getCpuCores();
        log.info("CPU核心数: {}", cpuCores);

        // 从 metric_data 表查询真实趋势数据
        long intervalMinutes = java.time.Duration.between(startTime, endTime).toMinutes() / points;

        for (int i = 0; i < points; i++) {
            LocalDateTime slotStart = startTime.plusMinutes(i * intervalMinutes);
            LocalDateTime slotEnd = slotStart.plusMinutes(intervalMinutes);

            // 精确查询指定指标名称
            LambdaQueryWrapper<MetricData> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MetricData::getMetricName, metricName)
                   .ge(MetricData::getTimestamp, slotStart)
                   .lt(MetricData::getTimestamp, slotEnd);

            List<MetricData> metrics = metricDataMapper.selectList(wrapper);

            if (!metrics.isEmpty()) {
                // 对于 cpu.usage，需要除以核心数转换为百分比
                double avg = metrics.stream()
                    .mapToDouble(m -> m.getMetricValue().doubleValue())
                    .average()
                    .orElse(0.0);

                double max = metrics.stream()
                    .mapToDouble(m -> m.getMetricValue().doubleValue())
                    .max()
                    .orElse(0.0);

                // CPU使用率需要除以核心数
                if ("cpu".equals(metric) && cpuCores != null && cpuCores > 0) {
                    avg = avg / cpuCores;
                    max = max / cpuCores;
                }

                // 保留一位小数
                avgValues.add(Math.round(avg * 10.0) / 10.0);
                maxValues.add(Math.round(max * 10.0) / 10.0);
            } else {
                avgValues.add(0.0);
                maxValues.add(0.0);
            }

            // 格式化时间标签
            String timeLabel = slotStart.format(java.time.format.DateTimeFormatter.ofPattern(
                points > 12 ? "MM-dd HH:mm" : "HH:mm"
            ));
            timestamps.add(timeLabel);
        }

        trend.put("timestamps", timestamps);
        trend.put("avgValues", avgValues);
        trend.put("maxValues", maxValues);

        return trend;
    }

    // 辅助方法：将前端传入的 metric 转换为实际的指标名称
    private String convertMetricToName(String metric) {
        return switch (metric.toLowerCase()) {
            case "cpu" -> "cpu.usage";
            case "memory" -> "memory.usage";
            case "disk" -> "disk.usage";
            case "network" -> "network.rx.rate";
            default -> metric;
        };
    }

    // 获取CPU核心数
    private Double getCpuCores() {
        try {
            LambdaQueryWrapper<MetricData> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MetricData::getMetricName, "cpu.cores")
                   .orderByDesc(MetricData::getTimestamp);

            List<MetricData> results = metricDataMapper.selectList(wrapper);
            if (!results.isEmpty()) {
                return results.get(0).getMetricValue().doubleValue();
            }
        } catch (Exception e) {
            log.warn("获取CPU核心数失败: {}", e.getMessage());
        }
        return 1.0; // 默认返回1
    }

    // 新增缓存Key常量
    private static final String METRIC_DISTRIBUTION_KEY = "stats:metric:distribution:";
    private static final String ALERT_TREND_KEY = "stats:alert:trend:";
    private static final String PROBE_STATUS_TREND_KEY = "stats:probe:status:trend:";
    private static final String PROBE_RANKING_KEY = "stats:probe:ranking:";
    private static final int SHORT_CACHE_TTL = 5; // 5分钟
    private static final int LONG_CACHE_TTL = 15; // 15分钟

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetricDistribution(String timeRange) {
        log.info("获取指标分布统计, timeRange={}", timeRange);

        String cacheKey = METRIC_DISTRIBUTION_KEY + timeRange;

        // 尝试从缓存获取
        if (redisTemplate != null) {
            Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("从缓存获取指标分布数据");
                return cached;
            }
        }

        // 计算时间范围
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(timeRange);

        // 按指标名称分组统计
        LambdaQueryWrapper<MetricData> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(MetricData::getTimestamp, startTime, endTime);

        List<MetricData> metrics = metricDataMapper.selectList(wrapper);
        log.info("查询到{}条指标数据", metrics.size());

        // 按metricName分组聚合
        Map<String, Long> distribution = metrics.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                MetricData::getMetricName,
                java.util.stream.Collectors.counting()
            ));

        // 转换为前端需要的格式
        List<Map<String, Object>> result = distribution.entrySet().stream()
            .map(entry -> {
                Map<String, Object> item = new HashMap<>();
                item.put("name", translateMetricName(entry.getKey()));
                item.put("value", entry.getValue());
                return item;
            })
            .sorted((a, b) -> ((Long) b.get("value")).compareTo((Long) a.get("value")))
            .collect(java.util.stream.Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("distribution", result);
        response.put("total", metrics.size());

        // 缓存结果
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(cacheKey, response, LONG_CACHE_TTL, TimeUnit.MINUTES);
            log.info("指标分布数据已缓存，TTL={}分钟", LONG_CACHE_TTL);
        }

        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAlertTrend(String timeRange) {
        log.info("获取告警趋势统计, timeRange={}", timeRange);

        String cacheKey = ALERT_TREND_KEY + timeRange;

        // 尝试从缓存获取
        if (redisTemplate != null) {
            Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("从缓存获取告警趋势数据");
                return cached;
            }
        }

        // 计算时间范围和数据点数
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(timeRange);
        int points = calculateDataPoints(timeRange);

        long intervalMinutes = java.time.Duration.between(startTime, endTime).toMinutes() / points;

        List<String> timestamps = new ArrayList<>();
        Map<String, List<Integer>> series = new HashMap<>();
        series.put("CRITICAL", new ArrayList<>());
        series.put("MAJOR", new ArrayList<>());
        series.put("MINOR", new ArrayList<>());
        series.put("INFO", new ArrayList<>());

        // 按时间槽统计每个级别的告警数
        for (int i = 0; i < points; i++) {
            LocalDateTime slotStart = startTime.plusMinutes(i * intervalMinutes);
            LocalDateTime slotEnd = slotStart.plusMinutes(intervalMinutes);

            // 添加时间标签
            String timeLabel = slotStart.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
            timestamps.add(timeLabel);

            // 统计各级别告警
            for (String severity : Arrays.asList("CRITICAL", "MAJOR", "MINOR", "INFO")) {
                LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Alert::getSeverity, severity)
                       .between(Alert::getTriggeredAt, slotStart, slotEnd);

                Long count = alertMapper.selectCount(wrapper);
                series.get(severity).add(count.intValue());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("timestamps", timestamps);
        response.put("series", series);

        // 缓存结果
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(cacheKey, response, SHORT_CACHE_TTL, TimeUnit.MINUTES);
            log.info("告警趋势数据已缓存，TTL={}分钟", SHORT_CACHE_TTL);
        }

        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getProbeStatusTrend(String timeRange) {
        log.info("获取探针状态趋势统计, timeRange={}", timeRange);

        String cacheKey = PROBE_STATUS_TREND_KEY + timeRange;

        // 尝试从缓存获取
        if (redisTemplate != null) {
            Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("从缓存获取探针状态趋势数据");
                return cached;
            }
        }

        // 计算时间范围
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(timeRange);
        int points = calculateDataPoints(timeRange);

        long intervalMinutes = java.time.Duration.between(startTime, endTime).toMinutes() / points;

        List<String> timestamps = new ArrayList<>();
        Map<String, List<Integer>> series = new HashMap<>();
        series.put("ONLINE", new ArrayList<>());
        series.put("OFFLINE", new ArrayList<>());
        series.put("ERROR", new ArrayList<>());

        // 按时间槽统计探针状态
        for (int i = 0; i < points; i++) {
            LocalDateTime slotStart = startTime.plusMinutes(i * intervalMinutes);
            LocalDateTime slotEnd = slotStart.plusMinutes(intervalMinutes);

            // 添加时间标签
            String timeLabel = formatTimeLabel(slotStart, points);
            timestamps.add(timeLabel);

            // 统计各状态探针数
            Long onlineCount = probeMapper.selectCount(
                new LambdaQueryWrapper<Probe>().eq(Probe::getStatus, "online")
            );

            Long offlineCount = probeMapper.selectCount(
                new LambdaQueryWrapper<Probe>().eq(Probe::getStatus, "offline")
            );

            // ERROR状态基于特定条件判断（这里简化处理）
            Long errorCount = 0L;

            series.get("ONLINE").add(onlineCount.intValue());
            series.get("OFFLINE").add(offlineCount.intValue());
            series.get("ERROR").add(errorCount.intValue());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("timestamps", timestamps);
        response.put("series", series);

        // 缓存结果
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(cacheKey, response, SHORT_CACHE_TTL, TimeUnit.MINUTES);
            log.info("探针状态趋势数据已缓存，TTL={}分钟", SHORT_CACHE_TTL);
        }

        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getProbeRanking(String timeRange, String metric) {
        log.info("获取探针排行统计, timeRange={}, metric={}", timeRange, metric);

        String cacheKey = PROBE_RANKING_KEY + timeRange + ":" + metric;

        // 尝试从缓存获取
        if (redisTemplate != null) {
            Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("从缓存获取探针排行数据");
                return cached;
            }
        }

        // 计算时间范围
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(timeRange);

        // 查询所有探针
        List<Probe> probes = probeMapper.selectList(null);
        log.info("查询到{}个探针", probes.size());

        // 为每个探针计算统计数据
        List<Map<String, Object>> ranking = probes.stream()
            .map(probe -> {
                // 数据采集量
                LambdaQueryWrapper<MetricData> dataWrapper = new LambdaQueryWrapper<>();
                dataWrapper.eq(MetricData::getProbeId, probe.getId())
                          .between(MetricData::getTimestamp, startTime, endTime);
                Long dataCount = metricDataMapper.selectCount(dataWrapper);

                // 平均指标值
                LambdaQueryWrapper<MetricData> metricWrapper = new LambdaQueryWrapper<>();
                metricWrapper.eq(MetricData::getProbeId, probe.getId())
                           .eq(MetricData::getMetricName, metric)
                           .between(MetricData::getTimestamp, startTime, endTime);
                List<MetricData> metrics = metricDataMapper.selectList(metricWrapper);
                double avgValue = metrics.stream()
                    .mapToDouble(m -> m.getMetricValue().doubleValue())
                    .average()
                    .orElse(0.0);

                // 告警次数
                LambdaQueryWrapper<Alert> alertWrapper = new LambdaQueryWrapper<>();
                alertWrapper.eq(Alert::getProbeId, probe.getId())
                           .between(Alert::getTriggeredAt, startTime, endTime);
                Long alertCount = alertMapper.selectCount(alertWrapper);

                // 可用率计算
                int availability = calculateAvailability(probe, startTime, endTime);

                Map<String, Object> item = new HashMap<>();
                item.put("probeId", probe.getId());
                item.put("probeName", probe.getName());
                item.put("type", probe.getType());
                item.put("dataCount", dataCount);
                item.put("avgValue", Math.round(avgValue * 10.0) / 10.0);
                item.put("alertCount", alertCount);
                item.put("availability", availability);
                item.put("lastActiveTime", probe.getLastHeartbeat() != null ?
                    probe.getLastHeartbeat().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) :
                    "-");
                return item;
            })
            .sorted((a, b) -> ((Long) b.get("dataCount")).compareTo((Long) a.get("dataCount")))
            .limit(20) // 限制返回前20名
            .collect(java.util.stream.Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("ranking", ranking);

        // 缓存结果
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(cacheKey, response, LONG_CACHE_TTL, TimeUnit.MINUTES);
            log.info("探针排行数据已缓存，TTL={}分钟", LONG_CACHE_TTL);
        }

        return response;
    }

    // 辅助方法：计算开始时间
    private LocalDateTime calculateStartTime(String timeRange) {
        return switch (timeRange) {
            case "1h" -> LocalDateTime.now().minusHours(1);
            case "6h" -> LocalDateTime.now().minusHours(6);
            case "24h" -> LocalDateTime.now().minusHours(24);
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            default -> LocalDateTime.now().minusHours(24);
        };
    }

    // 辅助方法：计算数据点数
    private int calculateDataPoints(String timeRange) {
        return switch (timeRange) {
            case "1h", "6h" -> 12;
            case "24h" -> 24;
            case "7d" -> 7;
            case "30d" -> 30;
            default -> 24;
        };
    }

    // 辅助方法：翻译指标名称
    private String translateMetricName(String metricName) {
        if (metricName == null || metricName.isEmpty()) {
            return "未知指标";
        }

        String lowerName = metricName.toLowerCase();

        // 根据指标名称前缀分类
        if (lowerName.startsWith("cpu.")) {
            return formatMetricLabel("CPU", metricName);
        } else if (lowerName.startsWith("memory.") || lowerName.startsWith("mem.")) {
            return formatMetricLabel("内存", metricName);
        } else if (lowerName.startsWith("disk.")) {
            return formatMetricLabel("磁盘", metricName);
        } else if (lowerName.startsWith("network.") || lowerName.startsWith("net.")) {
            return formatMetricLabel("网络", metricName);
        } else if (lowerName.startsWith("jvm.")) {
            return formatMetricLabel("JVM", metricName);
        } else if (lowerName.startsWith("system.")) {
            return formatMetricLabel("系统", metricName);
        } else if (lowerName.startsWith("app.") || lowerName.startsWith("application.")) {
            return formatMetricLabel("应用", metricName);
        } else if (lowerName.startsWith("database.") || lowerName.startsWith("db.")) {
            return formatMetricLabel("数据库", metricName);
        } else {
            // 对于其他指标，显示美化后的名称
            return beautifyMetricName(metricName);
        }
    }

    // 格式化指标标签
    private String formatMetricLabel(String category, String metricName) {
        String suffix = metricName.substring(metricName.indexOf('.') + 1);
        // 将下划线和点替换为中文分隔符
        suffix = suffix.replace(".", "·");
        suffix = suffix.replace("_", " ");
        return category + " " + suffix;
    }

    // 美化指标名称
    private String beautifyMetricName(String metricName) {
        // 将点分格式转换为可读格式
        String[] parts = metricName.split("\\.");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            // 首字母大写
            if (part.length() > 0) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1));
            }
            if (i < parts.length - 1) {
                result.append("·");
            }
        }

        return result.toString();
    }

    // 辅助方法：格式化时间标签
    private String formatTimeLabel(LocalDateTime dateTime, int totalPoints) {
        if (totalPoints == 7) {
            // 7天显示星期
            return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("E", java.util.Locale.CHINESE));
        } else {
            return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
        }
    }

    // 辅助方法：计算可用率
    private int calculateAvailability(Probe probe, LocalDateTime start, LocalDateTime end) {
        if (probe == null || probe.getLastHeartbeat() == null) {
            return 0;
        }

        // 如果最后心跳时间在查询范围内，认为可用
        if (probe.getLastHeartbeat().isAfter(start) &&
            probe.getLastHeartbeat().isBefore(end)) {
            return "online".equals(probe.getStatus()) ? 100 : 0;
        }

        return "online".equals(probe.getStatus()) ? 99 : 0;
    }
}
