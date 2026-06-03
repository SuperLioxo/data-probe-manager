package com.lixin.probe.agent.cdc;

import com.lixin.probe.agent.plugin.api.CDCPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDC插件加载器
 * 通过Java SPI机制自动发现并加载所有CDCPlugin实现
 */
public class CDCPluginLoader {

    private static final Logger log = LoggerFactory.getLogger(CDCPluginLoader.class);

    /** 缓存已加载的插件：databaseType -> CDCPlugin */
    private static final Map<String, CDCPlugin> pluginCache = new ConcurrentHashMap<>();

    /** 所有已发现的插件列表 */
    private static volatile List<CDCPlugin> allPlugins = List.of();

    private CDCPluginLoader() {}

    /**
     * 加载所有CDC插件（通过ServiceLoader）
     */
    public static synchronized List<CDCPlugin> loadPlugins() {
        if (!allPlugins.isEmpty()) {
            return allPlugins;
        }

        ServiceLoader<CDCPlugin> loader = ServiceLoader.load(CDCPlugin.class);
        List<CDCPlugin> plugins = new ArrayList<>();

        for (CDCPlugin plugin : loader) {
            plugins.add(plugin);
            // 按databaseType建立索引（mysql-binlog -> mysql, postgresql-wal -> postgresql）
            String subType = plugin.getSubType();
            if (subType != null) {
                // mysql-binlog -> mysql, postgresql-wal -> postgresql
                String dbType = subType.split("-")[0];
                pluginCache.put(dbType, plugin);
            }
            log.info("[CDC] 发现CDC插件: id={}, name={}, subType={}, version={}",
                    plugin.getPluginId(), plugin.getName(), plugin.getSubType(), plugin.getVersion());
        }

        allPlugins = Collections.unmodifiableList(plugins);
        log.info("[CDC] CDC插件加载完成: 共 {} 个插件", plugins.size());
        return allPlugins;
    }

    /**
     * 根据数据库类型查找对应的CDC插件
     * @param databaseType 数据库类型（mysql、postgresql等）
     * @return 匹配的CDC插件，未找到返回null
     */
    public static CDCPlugin findPlugin(String databaseType) {
        if (databaseType == null) return null;
        if (pluginCache.isEmpty()) {
            loadPlugins();
        }
        return pluginCache.get(databaseType.toLowerCase());
    }

    /**
     * 获取所有已加载的插件
     */
    public static List<CDCPlugin> getAllPlugins() {
        if (allPlugins.isEmpty()) {
            loadPlugins();
        }
        return allPlugins;
    }
}
