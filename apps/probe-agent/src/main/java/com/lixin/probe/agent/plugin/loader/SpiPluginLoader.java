package com.lixin.probe.agent.plugin.loader;

import com.lixin.probe.agent.plugin.api.DatabasePlugin;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * SPI 插件加载器
 * 负责从运行时加载 SPI 插件 JAR 文件
 *
 * 使用 Java SPI (Service Provider Interface) 机制动态加载数据库插件
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Component
public class SpiPluginLoader {

    private static final Logger log = LoggerFactory.getLogger(SpiPluginLoader.class);
    /**
     * SPI 插件目录（相对于项目根目录）
     */
    private static final String SPI_PLUGIN_DIR = "./plugin/spi-plugins/";

    /**
     * 插件缓存：key=dbType 或 dbType_version, value=DatabasePlugin
     */
    private final Map<String, DatabasePlugin> pluginCache = new ConcurrentHashMap<>();

    /**
     * ClassLoader缓存：key=标识符, value=URLClassLoader
     */
    private final Map<String, URLClassLoader> classLoaders = new ConcurrentHashMap<>();

    /**
     * 初始化插件加载器
     * 在Spring启动后自动调用，加载所有可用的SPI插件
     */
    @PostConstruct
    public void init() {
        log.info("初始化SPI插件加载器...");
        loadPluginsFromDirectory();
        log.info("SPI插件加载完成，共加载{}个插件", pluginCache.size());
    }

    /**
     * 从插件目录加载所有 SPI 插件
     */
    public void loadPluginsFromDirectory() {
        try {
            // 1. 解析插件目录
            Path pluginDir = resolvePluginDir();
            List<URL> jarUrls = new ArrayList<>();

            if (pluginDir != null) {
                log.info("SPI插件目录: {}", pluginDir);

                // 2. 扫描JAR文件
                try (Stream<Path> paths = Files.walk(pluginDir)) {
                    paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".jar"))
                         .forEach(path -> {
                             try {
                                 jarUrls.add(path.toUri().toURL());
                                 log.info("发现插件JAR: {}", path.getFileName());
                             } catch (Exception e) {
                                 log.error("无效的JAR文件: {}", path, e);
                             }
                         });
                }
            }

            // 3. 如果没有找到JAR文件，尝试从classpath加载
            ClassLoader classLoader;
            if (jarUrls.isEmpty()) {
                log.info("插件目录中未发现JAR文件，尝试从classpath加载插件");
                classLoader = getClass().getClassLoader();
            } else {
                // 创建 URLClassLoader
                classLoader = new URLClassLoader(
                    jarUrls.toArray(new URL[0]),
                    getClass().getClassLoader()
                );
                classLoaders.put("default", (URLClassLoader) classLoader);
            }

            // 4. 使用 ServiceLoader 加载插件
            ServiceLoader<DatabasePlugin> serviceLoader =
                ServiceLoader.load(DatabasePlugin.class, classLoader);

            // 5. 注册所有找到的插件
            int loadedCount = 0;
            for (DatabasePlugin plugin : serviceLoader) {
                try {
                    registerPlugin(plugin);
                    loadedCount++;
                    log.info("成功加载SPI插件: {} (type: {}, version: {})",
                        plugin.getPluginId(), plugin.getDbType(), plugin.getVersion());
                } catch (Exception e) {
                    log.error("加载SPI插件失败: {}", plugin.getPluginId(), e);
                }
            }

            if (loadedCount == 0) {
                log.warn("未找到任何SPI插件实现");
            } else {
                log.info("从classpath成功加载 {} 个SPI插件", loadedCount);
            }

        } catch (Exception e) {
            log.error("加载SPI插件失败", e);
        }
    }

    /**
     * 解析插件目录位置
     * 支持多种路径候选，兼容不同运行环境
     *
     * @return 插件目录路径，如果找不到返回null
     */
    private Path resolvePluginDir() {
        // 候选目录列表
        String[] candidates = {
            "plugin/spi-plugins",
            "../plugin/spi-plugins",
            "../../plugin/spi-plugins",
            "apps/probe-agent/plugin/spi-plugins"
        };

        // 1. 尝试预定义的候选目录
        for (String candidate : candidates) {
            Path p = Paths.get(candidate).toAbsolutePath().normalize();
            if (Files.exists(p) && Files.isDirectory(p)) {
                log.info("找到插件目录: {}", p);
                return p;
            }
        }

        // 2. 通过类文件位置向上查找项目根目录下的 plugin/spi-plugins/
        try {
            URL classLocation = SpiPluginLoader.class.getProtectionDomain().getCodeSource().getLocation();
            Path classPath = Paths.get(classLocation.toURI()).toAbsolutePath();
            // classPath 可能是 .../probe-agent/target/classes 或 jar 所在目录
            Path dir = Files.isDirectory(classPath) ? classPath : classPath.getParent();

            for (int i = 0; i < 6; i++) {
                if (dir == null) break;
                Path pluginCandidate = dir.resolve("plugin/spi-plugins");
                if (Files.exists(pluginCandidate) && Files.isDirectory(pluginCandidate)) {
                    log.info("通过类路径定位到插件目录: {}", pluginCandidate);
                    return pluginCandidate;
                }
                dir = dir.getParent();
            }
        } catch (Exception e) {
            log.debug("通过类路径定位插件目录失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 注册插件到缓存
     *
     * @param plugin 数据库插件
     */
    private void registerPlugin(DatabasePlugin plugin) {
        String dbType = plugin.getDbType();

        // 注册默认版本
        pluginCache.put(dbType.toLowerCase(), plugin);

        // 按版本注册
        String[] versions = plugin.getVersionRange().split(",");
        for (String version : versions) {
            String key = dbType.toLowerCase() + "_" + version.trim();
            pluginCache.put(key, plugin);
        }
    }

    /**
     * 获取插件（默认版本）
     *
     * @param dbType 数据库类型（mysql, postgresql, oracle等）
     * @return 数据库插件，如果不存在返回null
     */
    public DatabasePlugin getPlugin(String dbType) {
        if (dbType == null || dbType.isEmpty()) {
            return null;
        }
        return pluginCache.get(dbType.toLowerCase());
    }

    /**
     * 获取插件（指定版本）
     *
     * @param dbType  数据库类型
     * @param version 数据库版本（可选）
     * @return 数据库插件，如果不存在返回null
     */
    public DatabasePlugin getPlugin(String dbType, String version) {
        if (dbType == null || dbType.isEmpty()) {
            return null;
        }

        if (version == null || version.isEmpty()) {
            return getPlugin(dbType);
        }

        String key = dbType.toLowerCase() + "_" + version.trim();
        return pluginCache.getOrDefault(key, getPlugin(dbType));
    }

    /**
     * 获取所有已加载的插件
     *
     * @return 所有插件的集合（去重后）
     */
    public Collection<DatabasePlugin> getAllPlugins() {
        // 按pluginId去重
        Map<String, DatabasePlugin> uniquePlugins = new LinkedHashMap<>();
        for (DatabasePlugin plugin : pluginCache.values()) {
            uniquePlugins.putIfAbsent(plugin.getPluginId(), plugin);
        }
        return uniquePlugins.values();
    }

    /**
     * 获取支持的数据库类型列表
     *
     * @return 数据库类型列表
     */
    public Set<String> getSupportedTypes() {
        Set<String> types = new HashSet<>();
        for (DatabasePlugin plugin : getAllPlugins()) {
            types.add(plugin.getDbType());
        }
        return types;
    }

    /**
     * 检查是否支持指定数据库类型
     *
     * @param dbType 数据库类型
     * @return 是否支持
     */
    public boolean isSupported(String dbType) {
        return getPlugin(dbType) != null;
    }

    /**
     * 热重载SPI插件
     * 关闭旧的ClassLoader，重新扫描并加载插件
     *
     * @return 加载的插件数量
     */
    public synchronized int reloadPlugins() {
        log.info("开始热重载SPI插件...");

        // 清除缓存
        int oldSize = pluginCache.size();
        pluginCache.clear();

        // 重新加载
        loadPluginsFromDirectory();
        int newSize = pluginCache.size();

        log.info("SPI插件重载完成: {} -> {}", oldSize, newSize);
        return newSize;
    }

    /**
     * 获取插件统计信息
     *
     * @return 统计信息Map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPlugins", getAllPlugins().size());
        stats.put("supportedTypes", getSupportedTypes());
        stats.put("pluginCacheSize", pluginCache.size());
        return stats;
    }
}
