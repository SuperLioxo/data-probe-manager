package com.lixin.probe.plugin;

import java.util.Map;

/**
 * 数据库插件 SPI 接口
 * 所有动态加载的数据库插件必须实现此接口
 *
 * @author Claude Code
 * @since 1.0
 */
public interface DatabasePlugin {

    /**
     * 获取插件ID
     */
    String getPluginId();

    /**
     * 获取插件名称
     */
    String getName();

    /**
     * 获取插件类型
     */
    String getType();

    /**
     * 获取插件版本
     */
    String getVersion();

    /**
     * 获取插件描述
     */
    String getDescription();

    /**
     * 获取数据库类型
     */
    String getDbType();

    /**
     * 获取支持的版本范围
     */
    String getVersionRange();

    /**
     * 构建JDBC连接URL
     */
    String buildUrl(Map<String, Object> params);

    /**
     * 转义表名
     */
    String escapeTableName(String fullTableName);

    /**
     * 获取数据行数SQL
     */
    String getCountSql(boolean isPrecise, Map<String, Object> params);

    /**
     * 获取存储空间SQL
     */
    String getStorageSql(Map<String, Object> params);

    /**
     * 获取默认端口
     */
    int getDefaultPort();

    /**
     * 获取测试连接SQL
     */
    String getTestSql();

    /**
     * 是否支持该数据库版本
     */
    boolean isVersionSupported(String version);
}
