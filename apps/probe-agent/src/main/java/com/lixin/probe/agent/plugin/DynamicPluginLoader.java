package com.lixin.probe.agent.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 动态插件加载器（探针端）
 *
 * <p>负责运行时动态加载、卸载、重载插件。</p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
public class DynamicPluginLoader {

    private static final Logger log = LoggerFactory.getLogger(DynamicPluginLoader.class);
    // 插件类加载器缓存
    private final Map<String, URLClassLoader> loaders = new ConcurrentHashMap<>();

    // 插件实例缓存 - 使用 SPI 接口
    private final Map<String, com.lixin.probe.plugin.DatabasePlugin> plugins = new ConcurrentHashMap<>();

    // 插件元数据缓存
    private final Map<String, PluginMetadata> metadata = new ConcurrentHashMap<>();

    // 插件状态缓存
    private final Map<String, PluginStatus> statusMap = new ConcurrentHashMap<>();

    // 插件加载时间
    private final Map<String, LocalDateTime> loadTimeMap = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 插件存储目录
    private static final String PLUGIN_DIR = "./infra/plugins/";

    /**
     * 插件元数据
     */
    public static class PluginMetadata {
        private String pluginId;
        private String name;
        private String type;
        private String version;
        private String description;
        private String mainClass;
        private String author;
        private List<String> dependencies;
        private String minAgentVersion;

        // Getters and Setters
        public String getPluginId() { return pluginId; }
        public void setPluginId(String pluginId) { this.pluginId = pluginId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMainClass() { return mainClass; }
        public void setMainClass(String mainClass) { this.mainClass = mainClass; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
        public String getMinAgentVersion() { return minAgentVersion; }
        public void setMinAgentVersion(String minAgentVersion) { this.minAgentVersion = minAgentVersion; }
    }

    /**
     * 插件状态
     */
    public enum PluginStatus {
        NOT_LOADED,   // 未加载
        LOADED,       // 已加载
        ACTIVE,       // 运行中
        ERROR         // 错误
    }

    /**
     * 初始化插件目录
     */
    public void init() {
        File dir = new File(PLUGIN_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("创建插件目录: {}", PLUGIN_DIR);
        }
    }

    /**
     * 动态加载插件
     *
     * @param pluginId 插件ID
     * @param jarFile JAR文件
     */
    public synchronized void loadPlugin(String pluginId, File jarFile) throws Exception {
        log.info("开始加载插件: pluginId={}, jar={}", pluginId, jarFile.getAbsolutePath());

        // 1. 验证插件
        validatePlugin(jarFile);

        // 2. 读取插件元数据
        PluginMetadata meta = readPluginMetadata(jarFile);

        // 3. 检查是否已加载
        if (loaders.containsKey(pluginId)) {
            log.warn("插件已加载，将先卸载: pluginId={}", pluginId);
            unloadPlugin(pluginId);
        }

        // 4. 创建类加载器
        URLClassLoader loader = createClassLoader(jarFile);

        // 5. 加载主类并实例化
        com.lixin.probe.plugin.DatabasePlugin plugin = instantiatePlugin(loader, meta.getMainClass());

        // 6. 注册插件
        loaders.put(pluginId, loader);
        plugins.put(pluginId, plugin);
        metadata.put(pluginId, meta);
        statusMap.put(pluginId, PluginStatus.LOADED);
        loadTimeMap.put(pluginId, LocalDateTime.now());

        log.info("插件加载成功: pluginId={}, type={}, version={}",
                pluginId, meta.getType(), meta.getVersion());
    }

    /**
     * 热卸载插件
     *
     * @param pluginId 插件ID
     */
    public synchronized void unloadPlugin(String pluginId) throws Exception {
        log.info("开始卸载插件: pluginId={}", pluginId);

        URLClassLoader loader = loaders.get(pluginId);
        com.lixin.probe.plugin.DatabasePlugin plugin = plugins.get(pluginId);

        if (loader == null) {
            log.warn("插件未加载: pluginId={}", pluginId);
            return;
        }

        try {
            // 1. 清理插件资源
            if (plugin != null) {
                cleanupPlugin(plugin);
            }

            // 2. 关闭类加载器
            loader.close();

            // 3. 移除注册
            loaders.remove(pluginId);
            plugins.remove(pluginId);
            metadata.remove(pluginId);
            statusMap.put(pluginId, PluginStatus.NOT_LOADED);
            loadTimeMap.remove(pluginId);

            log.info("插件卸载成功: pluginId={}", pluginId);

        } catch (Exception e) {
            log.error("插件卸载失败: pluginId={}", pluginId, e);
            throw new RuntimeException("插件卸载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 热重载插件
     *
     * @param pluginId 插件ID
     */
    public synchronized void reloadPlugin(String pluginId) throws Exception {
        log.info("开始重载插件: pluginId={}", pluginId);

        // 1. 卸载旧版本
        if (loaders.containsKey(pluginId)) {
            unloadPlugin(pluginId);
        }

        // 2. 加载新版本
        File jarFile = new File(PLUGIN_DIR + pluginId + ".jar");
        if (jarFile.exists()) {
            loadPlugin(pluginId, jarFile);
        } else {
            throw new RuntimeException("插件文件不存在: " + jarFile.getAbsolutePath());
        }

        log.info("插件重载成功: pluginId={}", pluginId);
    }

    /**
     * 获取插件实例
     *
     * @param pluginId 插件ID
     * @return 插件实例
     */
    public com.lixin.probe.plugin.DatabasePlugin getPlugin(String pluginId) {
        return plugins.get(pluginId);
    }

    /**
     * 获取插件状态
     *
     * @param pluginId 插件ID
     * @return 插件状态
     */
    public PluginStatus getPluginStatus(String pluginId) {
        return statusMap.getOrDefault(pluginId, PluginStatus.NOT_LOADED);
    }

    /**
     * 获取所有已加载的插件ID
     *
     * @return 插件ID列表
     */
    public Set<String> getLoadedPlugins() {
        return new HashSet<>(loaders.keySet());
    }

    /**
     * 检查插件是否已加载
     *
     * @param pluginId 插件ID
     * @return true=已加载, false=未加载
     */
    public boolean isPluginLoaded(String pluginId) {
        return loaders.containsKey(pluginId);
    }

    /**
     * 验证插件
     */
    private void validatePlugin(File jarFile) throws IOException {
        if (!jarFile.exists()) {
            throw new IllegalArgumentException("插件文件不存在: " + jarFile.getAbsolutePath());
        }

        if (!jarFile.getName().endsWith(".jar")) {
            throw new IllegalArgumentException("文件必须是JAR格式");
        }

        // 检查plugin.json是否存在
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry("META-INF/plugin.json");
            if (entry == null) {
                throw new IllegalArgumentException("缺少plugin.json描述文件");
            }
        }
    }

    /**
     * 读取插件元数据
     */
    private PluginMetadata readPluginMetadata(File jarFile) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry("META-INF/plugin.json");
            if (entry == null) {
                throw new IOException("缺少plugin.json描述文件");
            }

            try (var is = jar.getInputStream(entry)) {
                String json = new String(is.readAllBytes());
                JsonNode root = objectMapper.readTree(json);

                PluginMetadata meta = new PluginMetadata();
                if (root.has("pluginId")) meta.setPluginId(root.get("pluginId").asText());
                if (root.has("name")) meta.setName(root.get("name").asText());
                if (root.has("type")) meta.setType(root.get("type").asText());
                if (root.has("version")) meta.setVersion(root.get("version").asText());
                if (root.has("description")) meta.setDescription(root.get("description").asText());
                if (root.has("mainClass")) meta.setMainClass(root.get("mainClass").asText());
                if (root.has("author")) meta.setAuthor(root.get("author").asText());

                if (root.has("dependencies")) {
                    List<String> deps = new ArrayList<>();
                    root.get("dependencies").forEach(node -> deps.add(node.asText()));
                    meta.setDependencies(deps);
                }

                if (root.has("minAgentVersion")) {
                    meta.setMinAgentVersion(root.get("minAgentVersion").asText());
                }

                return meta;
            }
        }
    }

    /**
     * 创建类加载器
     */
    private URLClassLoader createClassLoader(File jarFile) throws MalformedURLException {
        URL jarUrl = jarFile.toURI().toURL();
        return new URLClassLoader(new URL[]{jarUrl}, getClass().getClassLoader());
    }

    /**
     * 实例化插件
     */
    private com.lixin.probe.plugin.DatabasePlugin instantiatePlugin(URLClassLoader loader, String mainClass) throws Exception {
        Class<?> pluginClass = loader.loadClass(mainClass);

        // 验证是否实现了 SPI DatabasePlugin 接口
        if (!com.lixin.probe.plugin.DatabasePlugin.class.isAssignableFrom(pluginClass)) {
            throw new IllegalArgumentException("主类必须实现 com.lixin.probe.plugin.DatabasePlugin 接口");
        }

        @SuppressWarnings("deprecation")
        Constructor<?> constructor = pluginClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (com.lixin.probe.plugin.DatabasePlugin) constructor.newInstance();
    }

    /**
     * 清理插件资源
     */
    private void cleanupPlugin(com.lixin.probe.plugin.DatabasePlugin plugin) {
        // 如果插件有清理方法，调用它
        log.debug("清理插件资源: {}", plugin.getClass().getName());
    }

    /**
     * 获取插件元数据
     *
     * @param pluginId 插件ID
     * @return 插件元数据
     */
    public PluginMetadata getPluginMetadata(String pluginId) {
        return metadata.get(pluginId);
    }

    /**
     * 获取已加载插件数量
     *
     * @return 插件数量
     */
    public int getLoadedPluginCount() {
        return loaders.size();
    }

    /**
     * 获取插件加载时间
     *
     * @param pluginId 插件ID
     * @return 加载时间
     */
    public LocalDateTime getLoadTime(String pluginId) {
        return loadTimeMap.get(pluginId);
    }
}
