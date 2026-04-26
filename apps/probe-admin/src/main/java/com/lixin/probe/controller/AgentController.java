package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.entity.Agent;
import com.lixin.probe.entity.FileProbe;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.AgentHeartbeatService;
import com.lixin.probe.service.AgentService;
import com.lixin.probe.service.ChangeDetectionService;
import com.lixin.probe.service.FileProbeService;
import com.lixin.probe.service.ProbeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent管理Controller
 * 处理Agent相关的API请求
 *
 * @author Claude Code
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    @Autowired
    @SuppressWarnings("all")
    private ProbeService probeService;

    @Autowired
    @SuppressWarnings("all")
    private FileProbeService fileProbeService;

    @Autowired(required = false)
    @SuppressWarnings("all")
    private AgentHeartbeatService agentHeartbeatService;

    @Autowired
    @SuppressWarnings("all")
    private AgentService agentService;

    @Autowired(required = false)
    @SuppressWarnings("all")
    private ChangeDetectionService changeDetectionService;

    @Autowired(required = false)
    @SuppressWarnings("all")
    private com.lixin.probe.service.DataSourceAlertService dataSourceAlertService;

    /**
     * 获取Agent需要监控的探针列表
     *
     * @param agentCode Agent编码
     * @return 探针列表
     */
    @GetMapping("/{agentCode}/probes")
    public Result<Map<String, Object>> getAgentProbes(@PathVariable String agentCode) {
        log.info("[Agent探针查询] Agent编码: {}", agentCode);

        try {
            // 查询属于该Agent的探针（从probe表）
            List<Probe> allProbes = probeService.list();

            // 查询属于该Agent的文件探针（从file_probe表）
            List<FileProbe> allFileProbes = fileProbeService.list();

            Map<String, Object> result = new HashMap<>();

            // 筛选属于该Agent的SYSTEM/DATABASE探针
            List<Probe> systemProbes = allProbes.stream()
                    .filter(probe -> {
                        if ("SYSTEM".equals(probe.getType())) {
                            return probe.getProbeKey() != null &&
                                   (probe.getProbeKey().equals(agentCode + "-system") ||
                                    probe.getProbeKey().contains(agentCode));
                        }
                        // 3. DATABASE类型探针：probe_key包含agentCode
                        else if ("DATABASE".equals(probe.getType())) {
                            return probe.getProbeKey() != null &&
                                   probe.getProbeKey().contains(agentCode);
                        }
                        return false;
                    })
                    .toList();

            // 筛选属于该Agent的FILE探针
            List<FileProbe> fileProbes = allFileProbes.stream()
                    .filter(probe -> probe.getProbeKey() != null &&
                                   probe.getProbeKey().contains(agentCode))
                    .toList();

            // 合并两种探针到统一列表（使用Map以保持通用性）
            List<Map<String, Object>> allAgentProbes = new ArrayList<>();

            // 添加SYSTEM/DATABASE探针
            for (Probe probe : systemProbes) {
                Map<String, Object> probeMap = new HashMap<>();
                probeMap.put("id", probe.getId());
                probeMap.put("probeKey", probe.getProbeKey());
                probeMap.put("name", probe.getName());
                probeMap.put("type", probe.getType());
                probeMap.put("status", probe.getStatus());
                probeMap.put("hostIp", probe.getHostIp());
                probeMap.put("port", probe.getPort());
                probeMap.put("collectInterval", probe.getCollectInterval());
                probeMap.put("lastHeartbeat", probe.getLastHeartbeat());
                probeMap.put("createTime", probe.getCreateTime());
                probeMap.put("updateTime", probe.getUpdateTime());
                allAgentProbes.add(probeMap);
            }

            // 添加FILE探针
            for (FileProbe probe : fileProbes) {
                Map<String, Object> probeMap = new HashMap<>();
                probeMap.put("id", probe.getId());
                probeMap.put("probeKey", probe.getProbeKey());
                probeMap.put("name", probe.getName());
                probeMap.put("type", probe.getType());
                probeMap.put("status", probe.getStatus());
                probeMap.put("hostIp", probe.getHostIp());
                probeMap.put("port", probe.getPort());
                probeMap.put("scanInterval", probe.getScanInterval());
                probeMap.put("scanPath", probe.getScanPath());
                probeMap.put("lastHeartbeat", probe.getLastHeartbeat());
                probeMap.put("createTime", probe.getCreateTime());
                probeMap.put("updateTime", probe.getUpdateTime());
                allAgentProbes.add(probeMap);
            }

            log.info("[Agent探针查询] 找到 {} 个探针属于Agent {} (SYSTEM: {}, FILE: {})",
                    allAgentProbes.size(), agentCode, systemProbes.size(), fileProbes.size());

            result.put("agentCode", agentCode);
            result.put("probes", allAgentProbes);
            result.put("count", allAgentProbes.size());

            return Result.success(result);

        } catch (Exception e) {
            log.error("[Agent探针查询] 查询失败: agentCode={}", agentCode, e);
            return Result.error("查询探针列表失败: " + e.getMessage());
        }
    }

    /**
     * Agent健康检查和探针同步
     * Agent定期调用此接口同步探针配置
     *
     * @param agentCode Agent编码
     * @return 需要监控的探针列表
     */
    @GetMapping("/{agentCode}/sync")
    public Result<Map<String, Object>> syncAgentProbes(@PathVariable String agentCode) {
        log.info("[Agent探针同步] Agent编码: {}", agentCode);
        return getAgentProbes(agentCode);
    }

    /**
     * 检查Agent程序在线状态
     *
     * @param agentCode Agent编码
     * @return Agent在线状态信息
     */
    @GetMapping("/{agentCode}/status")
    public Result<Map<String, Object>> getAgentStatus(@PathVariable String agentCode) {
        log.info("[Agent状态查询] Agent编码: {}", agentCode);

        try {
            Map<String, Object> result = new HashMap<>();

            if (agentHeartbeatService == null) {
                result.put("agentCode", agentCode);
                result.put("online", false);
                result.put("message", "Agent心跳服务未启用");
                return Result.success(result);
            }

            boolean online = agentHeartbeatService.isAgentOnline(agentCode);
            Long lastHeartbeat = agentHeartbeatService.getLastHeartbeat(agentCode);

            result.put("agentCode", agentCode);
            result.put("online", online);
            result.put("lastHeartbeat", lastHeartbeat);

            if (lastHeartbeat != null) {
                long ageSeconds = (Instant.now().toEpochMilli() - lastHeartbeat) / 1000;
                result.put("lastHeartbeatAge", ageSeconds);

                // 根据在线状态和心跳时间显示不同的消息
                if (online) {
                    result.put("message", "Agent在线");
                } else {
                    // 根据离线时间显示更友好的消息
                    String message;
                    if (ageSeconds < 60) {
                        message = "Agent刚启动，正在连接...（最后心跳: " + ageSeconds + "秒前）";
                    } else if (ageSeconds < 300) {
                        message = "Agent连接中...（最后心跳: " + ageSeconds + "秒前）";
                    } else {
                        message = "Agent离线（最后心跳: " + ageSeconds + "秒前）";
                    }
                    result.put("message", message);
                }
            } else {
                result.put("message", "Agent从未上报心跳");
            }

            log.info("[Agent状态查询] agentCode={}, online={}, lastHeartbeatAge={}s",
                    agentCode, online, result.getOrDefault("lastHeartbeatAge", "N/A"));

            return Result.success(result);

        } catch (Exception e) {
            log.error("[Agent状态查询] 查询失败: agentCode={}", agentCode, e);
            return Result.error("查询Agent状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有Agent列表
     */
    @GetMapping
    public Result<List<Agent>> getAllAgents() {
        log.info("[Agent列表] 获取所有Agent");
        try {
            List<Agent> agents = agentService.getAllAgents();
            log.info("[Agent列表] 找到 {} 个Agent", agents.size());
            return Result.success(agents);
        } catch (Exception e) {
            log.error("[Agent列表] 获取失败", e);
            return Result.error("获取Agent列表失败: " + e.getMessage());
        }
    }

    /**
     * Agent注册接口
     * Agent启动时调用此接口进行注册
     *
     * @param agentCode Agent代码
     * @param agentName Agent名称
     * @param hostIp 主机IP
     * @param port 端口
     * @param version 版本号
     * @return 注册结果
     */
    @PostMapping("/{agentCode}/register")
    public Result<Agent> registerAgent(
            @PathVariable String agentCode,
            @RequestParam(required = false) String agentName,
            @RequestParam String hostIp,
            @RequestParam Integer port,
            @RequestParam(required = false) String version) {
        log.info("[Agent注册] agentCode={}, agentName={}, hostIp={}, port={}, version={}",
                agentCode, agentName, hostIp, port, version);

        try {
            Agent agent = agentService.registerOrUpdateAgent(
                    agentCode,
                    agentName != null ? agentName : agentCode,
                    hostIp,
                    port,
                    version != null ? version : "2.0"
            );
            return Result.success(agent);
        } catch (Exception e) {
            log.error("[Agent注册] 注册失败: agentCode={}", agentCode, e);
            return Result.error("Agent注册失败: " + e.getMessage());
        }
    }

    /**
     * 获取Agent操作指引
     * 返回如何启动/停止/重启Agent的说明
     *
     * @param agentCode Agent代码
     * @return 操作指引
     */
    @GetMapping("/{agentCode}/guide")
    public Result<Map<String, Object>> getAgentGuide(@PathVariable String agentCode) {
        log.info("[Agent操作指引] agentCode={}", agentCode);

        try {
            Map<String, Object> guide = new HashMap<>();
            guide.put("agentCode", agentCode);

            // 检查Agent状态
            Agent agent = agentService.getByAgentCode(agentCode);
            boolean online = false;
            if (agent != null) {
                online = "online".equals(agent.getStatus());
            }

            // 生成操作指引
            Map<String, String> commands = new HashMap<>();
            commands.put("start", String.format(
                    "cd %s/apps/probe-agent && " +
                    "export PROBE_KEY=\"your-key\" && " +
                    "mvn spring-boot:run > /tmp/probe-agent.log 2>&1 &",
                    System.getProperty("user.dir", "/home/ovo/data-probe-manager")
            ));

            commands.put("stop", String.format(
                    "pkill -f \"probe-agent\""
            ));

            commands.put("restart", String.format(
                    "pkill -f \"probe-agent\" && " +
                    "sleep 2 && " +
                    "cd %s/apps/probe-agent && " +
                    "export PROBE_KEY=\"your-key\" && " +
                    "mvn spring-boot:run > /tmp/probe-agent.log 2>&1 &",
                    System.getProperty("user.dir", "/home/ovo/data-probe-manager")
            ));

            commands.put("checkStatus", String.format(
                    "ps aux | grep probe-agent | grep -v grep"
            ));

            commands.put("viewLogs", "tail -f /tmp/probe-agent.log");

            guide.put("online", online);
            guide.put("commands", commands);

            guide.put("startGuide", online ?
                    "Agent当前在线，无需启动" :
                    "Agent当前离线，请执行以下命令启动：\n1. cd " + System.getProperty("user.dir", "/home/ovo/data-probe-manager") + "/apps/probe-agent\n2. export PROBE_KEY=\"your-key\"\n3. mvn spring-boot:run"
            );

            guide.put("stopGuide", online ?
                    "Agent当前在线，执行以下命令停止：\npkill -f \"probe-agent\"" :
                    "Agent已离线，无需停止"
            );

            return Result.success(guide);

        } catch (Exception e) {
            log.error("[Agent操作指引] 生成失败: agentCode={}", agentCode, e);
            return Result.error("生成操作指引失败: " + e.getMessage());
        }
    }

    /**
     * 更新Agent状态
     * 用于WebSocket Handler调用
     *
     * @param agentCode Agent代码
     * @param status 状态（online/offline）
     */
    public void updateAgentStatus(String agentCode, String status) {
        try {
            agentService.updateAgentStatus(agentCode, status);
        } catch (Exception e) {
            log.error("[Agent状态更新] 更新失败: agentCode={}, status={}", agentCode, status, e);
        }
    }

    /**
     * 接收Agent上报的CDC事件
     */
    @PostMapping("/{agentCode}/cdc-events")
    public Result<String> receiveCDCEvents(@PathVariable String agentCode, @RequestBody String eventsJson) {
        log.info("[Agent CDC] 收到CDC事件: agentCode={}", agentCode);
        try {
            if (changeDetectionService != null) {
                changeDetectionService.processCDCEvents(agentCode, eventsJson);
            }
            return Result.success("已接收");
        } catch (Exception e) {
            log.error("[Agent CDC] 处理CDC事件失败", e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }

    /**
     * 接收Agent上报的数据源心跳报告
     * Agent 发送格式：{"agentCode":"xxx", "reports":[{"probeKey":"...", "status":"online", ...}], "timestamp":...}
     */
    @PostMapping("/{agentCode}/datasource-heartbeat")
    public Result<String> receiveDatasourceHeartbeat(@PathVariable String agentCode, @RequestBody String heartbeatJson) {
        log.debug("[Agent心跳] 收到数据源心跳: agentCode={}", agentCode);
        try {
            com.alibaba.fastjson2.JSONObject payload = com.alibaba.fastjson2.JSON.parseObject(heartbeatJson);
            if (payload == null) {
                return Result.error("无效的JSON数据");
            }

            com.alibaba.fastjson2.JSONArray reports = payload.getJSONArray("reports");
            if (reports == null || reports.isEmpty()) {
                return Result.success("已接收（无报告数据）");
            }

            // 更新每个数据源对应探针的 lastHeartbeat 和 status
            java.util.List<java.util.Map<String, Object>> heartbeatData = new java.util.ArrayList<>();
            for (int i = 0; i < reports.size(); i++) {
                com.alibaba.fastjson2.JSONObject report = reports.getJSONObject(i);
                String probeKey = report.getString("probeKey");
                String status = report.getString("status");

                if (probeKey == null) continue;

                // 更新 probe 表
                try {
                    Probe probe = probeService.getByProbeKey(probeKey);
                    if (probe != null) {
                        probe.setStatus(status != null ? status : "online");
                        probe.setLastHeartbeat(java.time.LocalDateTime.now());
                        probe.setUpdateTime(java.time.LocalDateTime.now());
                        probeService.update(probe);
                        log.debug("[数据源心跳] 更新探针状态: probeKey={}, status={}", probeKey, status);
                    }
                } catch (Exception e) {
                    log.warn("[数据源心跳] 更新探针状态失败: probeKey={}", probeKey, e);
                }

                heartbeatData.add(report);
            }

            // 数据源告警检测
            if (dataSourceAlertService != null && !heartbeatData.isEmpty()) {
                try {
                    dataSourceAlertService.processHeartbeat(agentCode, heartbeatData);
                } catch (Exception e) {
                    log.warn("[Agent心跳] 数据源告警处理失败: {}", e.getMessage());
                }
            }

            return Result.success("已接收 " + reports.size() + " 个数据源状态");
        } catch (Exception e) {
            log.error("[Agent心跳] 处理数据源心跳失败", e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }

    /**
     * Agent 注册数据源连接信息
     * Agent 启动时调用此接口，将 database-config.yml 中的连接信息注册到 Admin
     */
    @PostMapping("/{agentCode}/datasource-register")
    public Result<String> registerDatasources(@PathVariable String agentCode, @RequestBody String configJson) {
        log.info("[数据源注册] Agent上报数据源配置: agentCode={}", agentCode);
        try {
            com.alibaba.fastjson2.JSONArray datasources = com.alibaba.fastjson2.JSON.parseArray(configJson);
            if (datasources == null || datasources.isEmpty()) {
                return Result.error("空的数据源配置");
            }

            int created = 0;
            int updated = 0;

            for (int i = 0; i < datasources.size(); i++) {
                com.alibaba.fastjson2.JSONObject ds = datasources.getJSONObject(i);
                String instanceId = ds.getString("instanceId");
                String dbType = ds.getString("databaseType");
                String host = ds.getString("host");
                Integer port = ds.getInteger("port");
                String dbName = ds.getString("databaseName");
                String username = ds.getString("username");
                String password = ds.getString("password");
                String schemas = ds.getString("schemas");

                if (instanceId == null || host == null) continue;

                // 按实例ID查找已有记录（用 name 字段存 instanceId）
                com.lixin.probe.entity.DatabaseConnection existing =
                    com.lixin.probe.mapper.DatabaseConnectionMapper.class != null
                        ? databaseConnectionMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.lixin.probe.entity.DatabaseConnection>()
                                .eq(com.lixin.probe.entity.DatabaseConnection::getName, instanceId))
                        : null;

                if (existing != null) {
                    existing.setDatabaseType(dbType);
                    existing.setDatabaseHost(host);
                    existing.setDatabasePort(port);
                    existing.setDatabaseName(dbName);
                    existing.setUsername(username);
                    existing.setPassword(password);
                    existing.setSchemas(schemas);
                    existing.setIsActive(true);
                    existing.setUpdatedAt(java.time.LocalDateTime.now());
                    databaseConnectionMapper.updateById(existing);
                    updated++;
                } else {
                    com.lixin.probe.entity.DatabaseConnection conn = new com.lixin.probe.entity.DatabaseConnection();
                    conn.setName(instanceId);
                    conn.setDatabaseType(dbType);
                    conn.setDatabaseHost(host);
                    conn.setDatabasePort(port);
                    conn.setDatabaseName(dbName);
                    conn.setUsername(username);
                    conn.setPassword(password);
                    conn.setSchemas(schemas);
                    conn.setIsActive(true);
                    conn.setCreatedAt(java.time.LocalDateTime.now());
                    conn.setUpdatedAt(java.time.LocalDateTime.now());
                    databaseConnectionMapper.insert(conn);
                    created++;

                    // 同步注册到汇聚表
                    if (aggregationService != null) {
                        try {
                            aggregationService.registerDataSource(instanceId, instanceId, "DATABASE",
                                    dbType, host, port, dbName, agentCode);
                        } catch (Exception ae) {
                            log.warn("[数据源注册] 汇聚注册失败: {}", ae.getMessage());
                        }
                    }
                }
            }

            log.info("[数据源注册] 完成: agentCode={}, 新增={}, 更新={}", agentCode, created, updated);
            return Result.success("注册完成: 新增 " + created + ", 更新 " + updated);
        } catch (Exception e) {
            log.error("[数据源注册] 失败: agentCode={}", agentCode, e);
            return Result.error("注册失败: " + e.getMessage());
        }
    }

    @Autowired
    private com.lixin.probe.mapper.DatabaseConnectionMapper databaseConnectionMapper;

    @Autowired(required = false)
    private com.lixin.probe.service.AggregationService aggregationService;

    @Autowired
    private com.lixin.probe.mapper.QualityRuleMapper qualityRuleMapper;

    @Autowired
    private javax.sql.DataSource dataSource;

    /**
     * Agent 拉取质量规则
     */
    @GetMapping("/{agentCode}/quality-rules")
    public Result<?> getQualityRules(@PathVariable String agentCode) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.lixin.probe.entity.QualityRule> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.lixin.probe.entity.QualityRule>()
                        .eq(com.lixin.probe.entity.QualityRule::getEnabled, true);
        return Result.success(qualityRuleMapper.selectList(wrapper));
    }

    /**
     * Agent 上报质量过滤的坏记录
     */
    @PostMapping("/{agentCode}/quality-bad-records")
    public Result<String> receiveBadRecords(@PathVariable String agentCode, @RequestBody String badRecordsJson) {
        log.debug("[质量过滤] 收到 Agent 坏记录: agentCode={}", agentCode);
        try {
            com.alibaba.fastjson2.JSONArray records = com.alibaba.fastjson2.JSON.parseArray(badRecordsJson);
            for (int i = 0; i < records.size(); i++) {
                com.alibaba.fastjson2.JSONObject rec = records.getJSONObject(i);
                // 存入 aggregation.quality_bad_records
                try {
                    String sql = "INSERT INTO aggregation.quality_bad_records " +
                            "(probe_key, database_name, table_name, column_name, rule_name, rule_type, severity, rejected_data, rejection_reason, detected_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, NOW())";
                    javax.sql.DataSource ds = dataSource;
                    try (java.sql.Connection conn = ds.getConnection();
                         java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, rec.getString("probeKey"));
                        ps.setString(2, rec.getString("databaseName"));
                        ps.setString(3, rec.getString("tableName"));
                        ps.setString(4, rec.getJSONArray("violations") != null && !rec.getJSONArray("violations").isEmpty()
                                ? rec.getJSONArray("violations").getJSONObject(0).getString("columnName") : null);
                        ps.setString(5, rec.getJSONArray("violations") != null && !rec.getJSONArray("violations").isEmpty()
                                ? rec.getJSONArray("violations").getJSONObject(0).getString("ruleName") : null);
                        ps.setString(6, rec.getJSONArray("violations") != null && !rec.getJSONArray("violations").isEmpty()
                                ? rec.getJSONArray("violations").getJSONObject(0).getString("ruleType") : null);
                        ps.setString(7, rec.getJSONArray("violations") != null && !rec.getJSONArray("violations").isEmpty()
                                ? rec.getJSONArray("violations").getJSONObject(0).getString("severity") : null);
                        ps.setString(8, com.alibaba.fastjson2.JSON.toJSONString(rec.get("data")));
                        ps.setString(9, com.alibaba.fastjson2.JSON.toJSONString(rec.get("violations")));
                        ps.executeUpdate();
                    }
                } catch (Exception e) {
                    log.warn("[质量过滤] 保存坏记录失败: {}", e.getMessage());
                }
            }
            return Result.success("已接收 " + records.size() + " 条坏记录");
        } catch (Exception e) {
            log.error("[质量过滤] 处理坏记录失败", e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }
}
