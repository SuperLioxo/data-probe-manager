package com.lixin.probe.agent.controller;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.plugin.loader.SpiPluginLoader;
import com.lixin.probe.agent.pojo.report.PluginReport;
import com.lixin.probe.agent.result.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent信息查询控制器
 * 提供给管理系统查询Agent状态的接口
 *
 * @author probe-agent
 * @since 1.0.0
 */
@RestController
@RequestMapping("/agent")
public class AgentInfoController {

    private static final Logger log = LoggerFactory.getLogger(AgentInfoController.class);

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private SpiPluginLoader pluginLoader;

    /**
     * 获取Agent基本信息
     */
    @GetMapping("/info")
    public CommonResult<Map<String, Object>> getAgentInfo() {
        try {
            Map<String, Object> info = Map.of(
                    "code", agentProperties.getCode(),
                    "host", getLocalHost(),
                    "port", agentProperties.getServer().getPort(),
                    "status", "ONLINE",
                    "startupTime", System.currentTimeMillis(),
                    "pluginCount", pluginLoader.getAllPlugins().size()
            );

            return CommonResult.success("获取Agent信息成功", info);
        } catch (Exception e) {
            log.error("获取Agent信息失败", e);
            return CommonResult.fail("获取Agent信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取插件列表
     */
    @GetMapping("/plugins")
    public CommonResult<List<PluginReport.PluginInfo>> getPlugins() {
        try {
            List<PluginReport.PluginInfo> plugins = pluginLoader.getAllPlugins()
                    .stream()
                    .map(plugin -> PluginReport.PluginInfo.builder()
                            .pluginId(plugin.getPluginId())
                            .name(plugin.getName())
                            .type(plugin.getType())
                            .version(plugin.getVersion())
                            .dbType(plugin.getDbType())
                            .versionRange(plugin.getVersionRange())
                            .description(plugin.getDescription())
                            .status("ACTIVE")
                            .loadTime(System.currentTimeMillis())
                            .build())
                    .collect(Collectors.toList());

            return CommonResult.success("获取插件列表成功", plugins);
        } catch (Exception e) {
            log.error("获取插件列表失败", e);
            return CommonResult.fail("获取插件列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取插件状态
     */
    @GetMapping("/plugins/{pluginId}/status")
    public CommonResult<Map<String, Object>> getPluginStatus(@PathVariable String pluginId) {
        try {
            var plugin = pluginLoader.getPlugin(pluginId);
            boolean loaded = (plugin != null);

            Map<String, Object> status = Map.of(
                    "pluginId", pluginId,
                    "loaded", loaded,
                    "status", loaded ? "ACTIVE" : "NOT_LOADED"
            );

            if (loaded && plugin != null) {
                status.put("name", plugin.getName());
                status.put("version", plugin.getVersion());
                status.put("dbType", plugin.getDbType());
            }

            return CommonResult.success("获取插件状态成功", status);
        } catch (Exception e) {
            log.error("获取插件状态失败: pluginId={}", pluginId, e);
            return CommonResult.fail("获取插件状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取插件统计信息
     */
    @GetMapping("/plugins/statistics")
    public CommonResult<Map<String, Object>> getPluginStatistics() {
        try {
            Map<String, Object> stats = pluginLoader.getStatistics();

            return CommonResult.success("获取插件统计成功", stats);
        } catch (Exception e) {
            log.error("获取插件统计失败", e);
            return CommonResult.fail("获取插件统计失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public CommonResult<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = Map.of(
                    "status", "UP",
                    "agentCode", agentProperties.getCode(),
                    "plugins", pluginLoader.getAllPlugins().size(),
                    "timestamp", System.currentTimeMillis()
            );

            return CommonResult.success("Agent运行正常", health);
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return CommonResult.fail("健康检查失败: " + e.getMessage());
        }
    }

    /**
     * 获取本地主机地址
     */
    private String getLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
