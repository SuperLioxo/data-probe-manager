package com.lixin.probe.agent.controller;

import com.lixin.probe.agent.plugin.DynamicPluginLoader;
import com.lixin.probe.agent.result.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 插件管理控制器（探针端）
 *
 * <p>接收后端的插件管理指令，执行插件加载、卸载、重载等操作。</p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@RestController
@RequestMapping("/agent/plugin")
public class PluginController {

    private static final Logger log = LoggerFactory.getLogger(PluginController.class);
    @Autowired
    private DynamicPluginLoader dynamicPluginLoader;

    /**
     * 加载插件
     *
     * @param request 加载请求
     * @return 加载结果
     */
    @PostMapping("/load")
    public CommonResult<Map<String, Object>> loadPlugin(@RequestBody PluginLoadRequest request) {
        log.info("收到加载插件请求: pluginId={}", request.getPluginId());

        try {
            // 查找插件JAR文件
            File jarFile = findPluginJar(request.getPluginId());
            if (jarFile == null || !jarFile.exists()) {
                return CommonResult.fail("插件文件不存在: " + request.getPluginId());
            }

            // 加载插件
            dynamicPluginLoader.loadPlugin(request.getPluginId(), jarFile);

            Map<String, Object> result = new HashMap<>();
            result.put("pluginId", request.getPluginId());
            result.put("status", dynamicPluginLoader.getPluginStatus(request.getPluginId()).name());
            result.put("loadTime", dynamicPluginLoader.getLoadTime(request.getPluginId()));

            log.info("插件加载成功: pluginId={}", request.getPluginId());
            return CommonResult.success("插件加载成功", result);

        } catch (Exception e) {
            log.error("插件加载失败: pluginId={}", request.getPluginId(), e);
            return CommonResult.fail("插件加载失败: " + e.getMessage());
        }
    }

    /**
     * 卸载插件
     *
     * @param pluginId 插件ID
     * @return 卸载结果
     */
    @PostMapping("/unload")
    public CommonResult<String> unloadPlugin(@RequestParam String pluginId) {
        log.info("收到卸载插件请求: pluginId={}", pluginId);

        try {
            dynamicPluginLoader.unloadPlugin(pluginId);
            log.info("插件卸载成功: pluginId={}", pluginId);
            return CommonResult.success("插件卸载成功");

        } catch (Exception e) {
            log.error("插件卸载失败: pluginId={}", pluginId, e);
            return CommonResult.fail("插件卸载失败: " + e.getMessage());
        }
    }

    /**
     * 重载插件
     *
     * @param pluginId 插件ID
     * @return 重载结果
     */
    @PostMapping("/reload")
    public CommonResult<String> reloadPlugin(@RequestParam String pluginId) {
        log.info("收到重载插件请求: pluginId={}", pluginId);

        try {
            dynamicPluginLoader.reloadPlugin(pluginId);
            log.info("插件重载成功: pluginId={}", pluginId);
            return CommonResult.success("插件重载成功");

        } catch (Exception e) {
            log.error("插件重载失败: pluginId={}", pluginId, e);
            return CommonResult.fail("插件重载失败: " + e.getMessage());
        }
    }

    /**
     * 查询插件状态
     *
     * @param pluginId 插件ID
     * @return 插件状态
     */
    @GetMapping("/status")
    public CommonResult<Map<String, Object>> getPluginStatus(@RequestParam String pluginId) {
        try {
            DynamicPluginLoader.PluginStatus status = dynamicPluginLoader.getPluginStatus(pluginId);
            DynamicPluginLoader.PluginMetadata metadata = dynamicPluginLoader.getPluginMetadata(pluginId);

            Map<String, Object> result = new HashMap<>();
            result.put("pluginId", pluginId);
            result.put("status", status.name());
            result.put("loaded", dynamicPluginLoader.isPluginLoaded(pluginId));

            if (metadata != null) {
                result.put("name", metadata.getName());
                result.put("type", metadata.getType());
                result.put("version", metadata.getVersion());
                result.put("description", metadata.getDescription());
            }

            return CommonResult.success("查询成功", result);

        } catch (Exception e) {
            log.error("查询插件状态失败: pluginId={}", pluginId, e);
            return CommonResult.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有已加载的插件列表
     *
     * @return 插件列表
     */
    @GetMapping("/list")
    public CommonResult<Map<String, Object>> listPlugins() {
        try {
            Set<String> loadedPlugins = dynamicPluginLoader.getLoadedPlugins();

            Map<String, Object> result = new HashMap<>();
            result.put("total", loadedPlugins.size());
            result.put("plugins", loadedPlugins);
            result.put("pluginCount", dynamicPluginLoader.getLoadedPluginCount());

            return CommonResult.success("查询成功", result);

        } catch (Exception e) {
            log.error("查询插件列表失败", e);
            return CommonResult.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 扫描本地可用插件（未加载的）
     *
     * @return 可用插件列表
     */
    @GetMapping("/scan")
    public CommonResult<Map<String, Object>> scanAvailablePlugins() {
        try {
            File pluginDir = new File("./infra/plugins/");
            if (!pluginDir.exists()) {
                pluginDir.mkdirs();
            }

            // 获取所有JAR文件
            File[] jarFiles = pluginDir.listFiles((dir, name) -> name.endsWith(".jar"));

            Set<String> loadedPlugins = dynamicPluginLoader.getLoadedPlugins();
            java.util.List<Map<String, String>> availablePlugins = new java.util.ArrayList<>();

            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    String fileName = jarFile.getName();
                    String pluginId = fileName.substring(0, fileName.length() - 4); // Remove .jar

                    // 只返回未加载的插件
                    if (!loadedPlugins.contains(pluginId)) {
                        Map<String, String> pluginInfo = new HashMap<>();
                        pluginInfo.put("pluginId", pluginId);
                        pluginInfo.put("fileName", fileName);
                        pluginInfo.put("filePath", jarFile.getAbsolutePath());
                        pluginInfo.put("size", String.valueOf(jarFile.length()));
                        availablePlugins.add(pluginInfo);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("total", availablePlugins.size());
            result.put("plugins", availablePlugins);

            return CommonResult.success("扫描成功", result);

        } catch (Exception e) {
            log.error("扫描插件失败", e);
            return CommonResult.fail("扫描失败: " + e.getMessage());
        }
    }

    /**
     * 获取插件完整状态（合并已加载和可用插件）
     *
     * @return 完整插件状态
     */
    @GetMapping("/full-status")
    public CommonResult<Map<String, Object>> getFullPluginStatus() {
        try {
            // 1. 获取已加载插件
            Set<String> loadedPlugins = dynamicPluginLoader.getLoadedPlugins();

            // 2. 扫描磁盘上的插件文件
            File pluginDir = new File("./infra/plugins/");
            if (!pluginDir.exists()) {
                pluginDir.mkdirs();
            }

            File[] jarFiles = pluginDir.listFiles((dir, name) -> name.endsWith(".jar"));

            // 3. 构建完整状态映射
            Map<String, Map<String, Object>> pluginStatusMap = new HashMap<>();

            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    String fileName = jarFile.getName();
                    String pluginId = fileName.substring(0, fileName.length() - 4);

                    Map<String, Object> pluginInfo = new HashMap<>();
                    pluginInfo.put("pluginId", pluginId);
                    pluginInfo.put("fileName", fileName);
                    pluginInfo.put("filePath", jarFile.getAbsolutePath());
                    pluginInfo.put("size", jarFile.length());

                    // 检查是否已加载
                    if (loadedPlugins.contains(pluginId)) {
                        pluginInfo.put("loaded", true);
                        pluginInfo.put("status", "LOADED");

                        // 获取元数据
                        try {
                            DynamicPluginLoader.PluginMetadata metadata =
                                dynamicPluginLoader.getPluginMetadata(pluginId);
                            if (metadata != null) {
                                pluginInfo.put("name", metadata.getName());
                                pluginInfo.put("type", metadata.getType());
                                pluginInfo.put("version", metadata.getVersion());
                                pluginInfo.put("description", metadata.getDescription());
                            }
                        } catch (Exception e) {
                            log.warn("获取插件元数据失败: pluginId={}", pluginId, e);
                        }
                    } else {
                        pluginInfo.put("loaded", false);
                        pluginInfo.put("status", "AVAILABLE");
                    }

                    pluginStatusMap.put(pluginId, pluginInfo);
                }
            }

            // 4. 构建响应
            Map<String, Object> result = new HashMap<>();
            result.put("plugins", pluginStatusMap);
            result.put("total", pluginStatusMap.size());
            result.put("loadedCount", loadedPlugins.size());
            result.put("availableCount", pluginStatusMap.size() - loadedPlugins.size());

            return CommonResult.success("查询成功", result);

        } catch (Exception e) {
            log.error("获取插件完整状态失败", e);
            return CommonResult.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 拉取插件（从后端下载插件JAR）
     *
     * @param pluginId 插件ID
     * @param jarUrl JAR文件下载URL
     * @param checksum MD5校验和
     * @return 拉取结果
     */
    @PostMapping("/pull")
    public CommonResult<String> pullPlugin(
            @RequestParam String pluginId,
            @RequestParam String jarUrl,
            @RequestParam String checksum) {

        log.info("收到拉取插件请求: pluginId={}, jarUrl={}", pluginId, jarUrl);

        try {
            // 1. 确保插件目录存在
            File pluginDir = new File("./infra/plugins/");
            if (!pluginDir.exists()) {
                pluginDir.mkdirs();
            }

            // 2. 下载JAR文件
            File jarFile = new File(pluginDir, pluginId + ".jar");

            log.info("开始下载插件: pluginId={}, dest={}", pluginId, jarFile.getAbsolutePath());

            try (InputStream in = new URL(jarUrl).openStream()) {
                Files.copy(in, jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("插件下载完成: pluginId={}, size={}", pluginId, jarFile.length());

            // 3. 校验MD5
            String actualMd5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(
                Files.readAllBytes(jarFile.toPath()));

            if (!actualMd5.equals(checksum)) {
                jarFile.delete();
                log.error("插件校验失败: pluginId={}, expected={}, actual={}",
                    pluginId, checksum, actualMd5);
                return CommonResult.fail("插件校验和不匹配");
            }

            log.info("插件校验成功: pluginId={}, md5={}", pluginId, actualMd5);

            // 4. 加载插件
            dynamicPluginLoader.loadPlugin(pluginId, jarFile);

            log.info("插件拉取并加载成功: pluginId={}", pluginId);
            return CommonResult.success("插件拉取并加载成功");

        } catch (Exception e) {
            log.error("插件拉取失败: pluginId={}", pluginId, e);
            return CommonResult.fail("插件拉取失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public CommonResult<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("loadedPlugins", dynamicPluginLoader.getLoadedPluginCount());
        health.put("pluginDir", "./infra/plugins/");
        return CommonResult.success("OK", health);
    }

    /**
     * 查找插件JAR文件
     */
    private File findPluginJar(String pluginId) {
        File pluginDir = new File("./infra/plugins/");
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }

        // 查找匹配的JAR文件
        File[] jarFiles = pluginDir.listFiles((dir, name) ->
                name.startsWith(pluginId) && name.endsWith(".jar"));

        if (jarFiles != null && jarFiles.length > 0) {
            return jarFiles[0];
        }

        // 尝试直接使用pluginId作为文件名
        File directFile = new File(pluginDir, pluginId + ".jar");
        if (directFile.exists()) {
            return directFile;
        }

        return null;
    }

    /**
     * 插件加载请求
     */
    public static class PluginLoadRequest {
        private String pluginId;

        public String getPluginId() {
            return pluginId;
        }

        public void setPluginId(String pluginId) {
            this.pluginId = pluginId;
        }
    }

    /**
     * 插件拉取请求
     */
    public static class PluginPullRequest {
        private String pluginId;
        private String serverUrl;
        private String version;

        public String getPluginId() {
            return pluginId;
        }

        public void setPluginId(String pluginId) {
            this.pluginId = pluginId;
        }

        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
