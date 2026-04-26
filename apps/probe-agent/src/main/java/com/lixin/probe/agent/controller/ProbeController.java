package com.lixin.probe.agent.controller;

import com.lixin.probe.agent.collector.SystemMetricsCollector;
import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.plugin.api.DatabasePlugin;
import com.lixin.probe.agent.plugin.loader.SpiPluginLoader;
import com.lixin.probe.agent.pojo.request.ProbeRequest;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.lixin.probe.agent.udp.MetricData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 探针 REST API 控制器
 * 提供探针状态、系统指标、数据库元数据查询等 REST 接口
 *
 * @author probe-agent
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/probe")
public class ProbeController {

    private static final Logger log = LoggerFactory.getLogger(ProbeController.class);
    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private SpiPluginLoader pluginLoader;

    @Autowired
    private SystemMetricsCollector metricsCollector;

    /**
     * 指标描述映射（中英文双语）
     */
    private static final Map<String, String> METRIC_DESC = new LinkedHashMap<>();
    static {
        METRIC_DESC.put("cpu.usage", "CPU使用率(%) / CPU usage (%)");
        METRIC_DESC.put("cpu.process", "当前进程CPU使用率(%) / Process CPU usage (%)");
        METRIC_DESC.put("cpu.load.1min", "CPU 1分钟平均负载 / CPU 1-minute load average");
        METRIC_DESC.put("memory.total", "物理内存总量(MB) / Total physical memory (MB)");
        METRIC_DESC.put("memory.used", "已使用内存(MB) / Used memory (MB)");
        METRIC_DESC.put("memory.usage", "内存使用率(%) / Memory usage (%)");
        METRIC_DESC.put("disk.total", "磁盘总容量(GB) / Total disk capacity (GB)");
        METRIC_DESC.put("disk.used", "磁盘已使用(GB) / Used disk (GB)");
        METRIC_DESC.put("disk.usage", "磁盘使用率(%) / Disk usage (%)");
        METRIC_DESC.put("network.sent", "网络发送字节数 / Network sent bytes");
        METRIC_DESC.put("network.received", "网络接收字节数 / Network received bytes");
    }

    /**
     * 探针状态总览
     * GET http://localhost:58081/api/probe/status
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "探针状态总览 / Probe status overview");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentCode", descValue(agentProperties.getCode(), "探针编码 / Agent code"));
        data.put("agentKey", descValue(agentProperties.getKey() != null ? "***" : "未配置", "认证密钥 / Auth key"));

        // 插件统计
        Map<String, Object> pluginStats = new LinkedHashMap<>();
        Collection<DatabasePlugin> plugins = pluginLoader.getAllPlugins();
        pluginStats.put("totalPlugins", descValue(plugins.size(), "插件总数 / Total plugins"));
        pluginStats.put("supportedTypes", descValue(pluginLoader.getSupportedTypes(), "支持的数据库类型 / Supported database types"));

        data.put("plugins", pluginStats);
        result.put("data", data);

        return result;
    }

    /**
     * 实时系统指标（缓存5秒）
     * GET http://localhost:58081/api/probe/metrics
     */
    @Cacheable(value = "systemMetrics", key = "'metrics'")
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "实时系统指标 / Real-time system metrics");

        // 采集系统指标
        List<MetricData> rawMetrics = metricsCollector.collectMetrics();
        List<Map<String, Object>> metricList = new ArrayList<>();

        for (MetricData metric : rawMetrics) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", metric.getName());
            item.put("value", metric.getValue());
            item.put("timestamp", metric.getTimestamp());
            item.put("desc", METRIC_DESC.getOrDefault(metric.getName(), metric.getName()));
            metricList.add(item);
        }

        result.put("data", metricList);
        return result;
    }

    /**
     * 数据库元数据探取（演示接口，需要通过查询参数传递数据库配置）
     * GET http://localhost:58081/api/probe/metadata?type=mysql&host=localhost&port=3306&name=testdb&username=root&password=123456
     */
    @GetMapping("/metadata")
    public Map<String, Object> metadata(ProbeRequest.DatabaseConfig config) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 参数校验
            if (config == null || config.getType() == null) {
                result.put("code", 400);
                result.put("message", "数据库类型参数不能为空 / Database type parameter is required");
                return result;
            }

            // 获取插件
            DatabasePlugin plugin = pluginLoader.getPlugin(config.getType());
            if (plugin == null) {
                result.put("code", 404);
                result.put("message", "未找到类型为 [" + config.getType() + "] 的数据库插件 / No database plugin found for type: " + config.getType());
                result.put("supportedTypes", pluginLoader.getSupportedTypes());
                return result;
            }

            // 构建探针请求
            ProbeRequest request = ProbeRequest.builder()
                    .id(System.currentTimeMillis())
                    .content(1) // METADATA = 1
                    .database(config)
                    .build();

            // 获取数据库连接
            Map<String, Object> connectionParams = new HashMap<>();
            connectionParams.put("host", config.getHost());
            connectionParams.put("port", config.getPort());
            connectionParams.put("name", config.getName());
            connectionParams.put("username", config.getUsername());
            connectionParams.put("password", config.getPassword());

            java.sql.Connection connection = null;
            try {
                connection = plugin.getConnection(connectionParams);

                // 探取元数据
                CompletableFuture<ProbeResponse.Metadata> future = plugin.getMetadata(connection, request);
                ProbeResponse.Metadata metadata = future.get();

                if (metadata == null || metadata.getDatabases() == null || metadata.getDatabases().isEmpty()) {
                    result.put("code", 500);
                    result.put("message", "元数据获取失败 / Failed to get metadata");
                    return result;
                }

                result.put("code", 200);
                result.put("message", "数据库元数据探取成功 / Database metadata retrieved successfully");
                result.put("pluginId", descValue(plugin.getPluginId(), "插件ID / Plugin ID"));
                result.put("pluginName", descValue(plugin.getName(), "插件名称 / Plugin name"));
                result.put("pluginVersion", descValue(plugin.getVersion(), "插件版本 / Plugin version"));

                // 获取第一个数据库的信息
                ProbeResponse.Metadata.Database db = metadata.getDatabases().values().iterator().next();

                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("type", descValue(db.getType(), "数据库类型 / Database type"));
                summary.put("host", descValue(db.getHost(), "数据库主机 / Database host"));
                summary.put("port", descValue(db.getPort(), "数据库端口 / Database port"));
                summary.put("name", descValue(db.getName(), "数据库名称 / Database name"));
                summary.put("tableCount", descValue(db.getTableCount(), "数据表总数 / Total tables"));
                summary.put("columnCount", descValue(db.getColumnCount(), "字段总数 / Total columns"));
                result.put("summary", summary);

                // 表列表（只显示概要信息）
                if (db.getTables() != null && !db.getTables().isEmpty()) {
                    Map<String, Object> tablesSummary = new LinkedHashMap<>();
                    for (Map.Entry<String, ProbeResponse.Metadata.Table> entry : db.getTables().entrySet()) {
                        ProbeResponse.Metadata.Table table = entry.getValue();
                        Map<String, Object> tableInfo = new LinkedHashMap<>();
                        tableInfo.put("name", table.getName());
                        tableInfo.put("comment", table.getComment() != null ? table.getComment() : "");
                        tableInfo.put("columnCount", table.getColumnCount());
                        tablesSummary.put(entry.getKey(), tableInfo);
                    }
                    result.put("tables", tablesSummary);
                    result.put("tablesCount", descValue(db.getTables().size(), "返回的表数量 / Number of tables returned"));
                }

            } finally {
                if (connection != null) {
                    try { connection.close(); } catch (Exception e) { log.error("关闭数据库连接失败", e); }
                }
            }

        } catch (Exception e) {
            log.error("元数据探取异常 / Metadata retrieval error", e);
            result.put("code", 500);
            result.put("message", "元数据获取异常 / Metadata retrieval error: " + e.getMessage());
        }

        return result;
    }

    /**
     * 数据库数据量探取（演示接口，需要通过查询参数传递数据库配置）
     * GET http://localhost:58081/api/probe/datasize?type=mysql&host=localhost&port=3306&name=testdb&username=root&password=123456
     */
    @GetMapping("/datasize")
    public Map<String, Object> datasize(ProbeRequest.DatabaseConfig config) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 参数校验
            if (config == null || config.getType() == null) {
                result.put("code", 400);
                result.put("message", "数据库类型参数不能为空 / Database type parameter is required");
                return result;
            }

            // 获取插件
            DatabasePlugin plugin = pluginLoader.getPlugin(config.getType());
            if (plugin == null) {
                result.put("code", 404);
                result.put("message", "未找到类型为 [" + config.getType() + "] 的数据库插件 / No database plugin found for type: " + config.getType());
                result.put("supportedTypes", pluginLoader.getSupportedTypes());
                return result;
            }

            // 构建探针请求
            ProbeRequest request = ProbeRequest.builder()
                    .id(System.currentTimeMillis())
                    .content(2) // DATA_SIZE = 2
                    .database(config)
                    .build();

            // 获取数据库连接
            Map<String, Object> connectionParams = new HashMap<>();
            connectionParams.put("host", config.getHost());
            connectionParams.put("port", config.getPort());
            connectionParams.put("name", config.getName());
            connectionParams.put("username", config.getUsername());
            connectionParams.put("password", config.getPassword());

            java.sql.Connection connection = null;
            try {
                connection = plugin.getConnection(connectionParams);

                // 探取数据量
                CompletableFuture<ProbeResponse.DataSize> future = plugin.getDataSize(connection, request);
                ProbeResponse.DataSize dataSize = future.get();

                if (dataSize == null || dataSize.getDatabases() == null || dataSize.getDatabases().isEmpty()) {
                    result.put("code", 500);
                    result.put("message", "数据量获取失败 / Failed to get data size");
                    return result;
                }

                result.put("code", 200);
                result.put("message", "数据库数据量探取成功 / Database data size retrieved successfully");
                result.put("pluginId", descValue(plugin.getPluginId(), "插件ID / Plugin ID"));
                result.put("pluginName", descValue(plugin.getName(), "插件名称 / Plugin name"));
                result.put("pluginVersion", descValue(plugin.getVersion(), "插件版本 / Plugin version"));

                // 获取第一个数据库的信息
                ProbeResponse.DataSize.Database db = dataSize.getDatabases().values().iterator().next();

                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("type", descValue(db.getType(), "数据库类型 / Database type"));
                summary.put("host", descValue(db.getHost(), "数据库主机 / Database host"));
                summary.put("port", descValue(db.getPort(), "数据库端口 / Database port"));
                summary.put("name", descValue(db.getName(), "数据库名称 / Database name"));
                summary.put("tableCount", descValue(db.getTableCount(), "数据表总数 / Total tables"));
                summary.put("columnCount", descValue(db.getColumnCount(), "字段总数 / Total columns"));
                summary.put("storage", descValue(formatBytes(db.getStorage()), "数据库总存储大小 / Total storage"));
                summary.put("rowCount", descValue(db.getRowCount(), "数据库总行数 / Total rows"));
                result.put("summary", summary);

                // 表列表（只显示概要信息）
                if (db.getTables() != null && !db.getTables().isEmpty()) {
                    List<Map<String, Object>> tableList = new ArrayList<>();
                    for (Map.Entry<String, ProbeResponse.DataSize.Table> entry : db.getTables().entrySet()) {
                        ProbeResponse.DataSize.Table table = entry.getValue();
                        Map<String, Object> tableInfo = new LinkedHashMap<>();
                        tableInfo.put("fullName", entry.getKey());
                        tableInfo.put("name", table.getName());
                        tableInfo.put("columnCount", table.getColumnCount());
                        tableInfo.put("rowCount", table.getRowCount());
                        tableInfo.put("storage", formatBytes(table.getStorage()));
                        tableList.add(tableInfo);
                    }
                    result.put("tables", tableList);
                    result.put("tablesCount", descValue(db.getTables().size(), "返回的表数量 / Number of tables returned"));
                }

            } finally {
                if (connection != null) {
                    try { connection.close(); } catch (Exception e) { log.error("关闭数据库连接失败", e); }
                }
            }

        } catch (Exception e) {
            log.error("数据量探取异常 / Data size retrieval error", e);
            result.put("code", 500);
            result.put("message", "数据量获取异常 / Data size retrieval error: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取 SPI 插件列表（缓存10分钟）
     * GET http://localhost:58081/api/probe/plugins
     */
    @Cacheable(value = "pluginsList", key = "'list'")
    @GetMapping("/plugins")
    public Map<String, Object> plugins() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "SPI 插件列表 / SPI plugins list");

        try {
            // 获取所有插件
            Collection<DatabasePlugin> plugins = pluginLoader.getAllPlugins();
            List<Map<String, Object>> pluginList = new ArrayList<>();

            for (DatabasePlugin plugin : plugins) {
                Map<String, Object> pluginInfo = new LinkedHashMap<>();
                pluginInfo.put("pluginId", plugin.getPluginId());
                pluginInfo.put("name", plugin.getName());
                pluginInfo.put("type", plugin.getType());
                pluginInfo.put("version", plugin.getVersion());
                pluginInfo.put("description", plugin.getDescription());
                pluginInfo.put("dbType", plugin.getDbType());
                pluginInfo.put("versionRange", plugin.getVersionRange());
                pluginInfo.put("defaultPort", plugin.getDefaultPort());
                pluginList.add(pluginInfo);
            }

            result.put("data", pluginList);
            result.put("total", pluginList.size());
            result.put("supportedTypes", pluginLoader.getSupportedTypes());

        } catch (Exception e) {
            log.error("获取插件列表失败", e);
            result.put("code", 500);
            result.put("message", "获取插件列表失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 热重载 SPI 插件（清除插件列表缓存）
     * POST http://localhost:58081/api/probe/plugins/reload
     */
    @CacheEvict(value = "pluginsList", key = "'list'")
    @org.springframework.web.bind.annotation.PostMapping("/plugins/reload")
    public Map<String, Object> reloadPlugins() {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            log.info("开始热重载 SPI 插件...");

            int oldSize = pluginLoader.getAllPlugins().size();
            int newSize = pluginLoader.reloadPlugins();

            result.put("code", 200);
            result.put("message", "SPI 插件热重载成功 / SPI plugins reloaded successfully");
            result.put("oldPluginCount", descValue(oldSize, "重载前插件数 / Old plugin count"));
            result.put("newPluginCount", descValue(newSize, "重载后插件数 / New plugin count"));
            result.put("reloaded", descValue(newSize, "重新加载的插件数 / Reloaded plugins"));

            log.info("SPI 插件热重载完成: {} -> {}", oldSize, newSize);

        } catch (Exception e) {
            log.error("热重载 SPI 插件失败", e);
            result.put("code", 500);
            result.put("message", "热重载失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取插件统计信息
     * GET http://localhost:58081/api/probe/plugins/statistics
     */
    @GetMapping("/plugins/statistics")
    public Map<String, Object> pluginStatistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "SPI 插件统计信息 / SPI plugins statistics");

        try {
            Map<String, Object> stats = pluginLoader.getStatistics();
            result.put("data", stats);

        } catch (Exception e) {
            log.error("获取插件统计信息失败", e);
            result.put("code", 500);
            result.put("message", "获取统计信息失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 创建带描述的值对象（中英文双语）
     */
    private Map<String, Object> descValue(Object value, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("desc", desc);
        return m;
    }

    /**
     * 格式化字节大小
     */
    private String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        double size = bytes;
        while (size >= 1024 && idx < units.length - 1) {
            size /= 1024;
            idx++;
        }
        return String.format("%.2f %s", size, units[idx]);
    }
}
