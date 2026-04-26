package com.lixin.probe.agent.websocket;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.config.DatabaseConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置热更新处理器
 * 处理CONFIG_UPDATE命令，支持更新database、system、agent三种类型的配置
 *
 * @author Claude Code
 * @since 1.0
 */
@Component
public class ConfigUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfigUpdateHandler.class);

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private DatabaseConfigManager databaseConfigManager;

    private MessageSender messageSender;

    /**
     * 设置消息发送器（由外部调用注入）
     */
    public void setMessageSender(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    /**
     * 处理配置更新
     *
     * @param payload 配置更新载荷，包含configType和config字段
     * @return 处理结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleConfigUpdate(Map<String, Object> payload) {
        String configType = payload.get("configType") != null ? payload.get("configType").toString() : null;
        Object configObj = payload.get("config");

        if (configType == null || configType.isEmpty()) {
            log.warn("配置更新失败：缺少configType字段");
            return buildResult(false, "缺少configType字段", null);
        }

        Map<String, Object> config;
        if (configObj instanceof Map) {
            config = (Map<String, Object>) configObj;
        } else {
            log.warn("配置更新失败：config字段格式错误，期望Map");
            return buildResult(false, "config字段格式错误", null);
        }

        log.info("处理配置更新: configType={}, configKeys={}", configType, config.keySet());

        try {
            switch (configType.toLowerCase()) {
                case "database":
                    return handleDatabaseConfig(config);
                case "system":
                    return handleSystemConfig(config);
                case "agent":
                    return handleAgentConfig(config);
                default:
                    log.warn("不支持的配置类型: {}", configType);
                    return buildResult(false, "不支持的配置类型: " + configType, null);
            }
        } catch (Exception e) {
            log.error("配置更新处理异常: configType={}", configType, e);
            return buildResult(false, "配置更新异常: " + e.getMessage(), null);
        }
    }

    /**
     * 处理数据库配置更新
     */
    private Map<String, Object> handleDatabaseConfig(Map<String, Object> config) {
        log.info("开始更新数据库配置");

        List<String> updatedFields = new ArrayList<>();

        try {
            AgentProperties.Database dbConfig = agentProperties.getModules().getDatabase();
            if (dbConfig == null) {
                return buildResult(false, "数据库模块配置未初始化", null);
            }

            // 更新数据库模块级配置
            if (config.containsKey("connectPoolSize")) {
                dbConfig.setConnectPoolSize(toInteger(config.get("connectPoolSize")));
                updatedFields.add("connectPoolSize");
            }
            if (config.containsKey("connectTimeout")) {
                dbConfig.setConnectTimeout(toInteger(config.get("connectTimeout")));
                updatedFields.add("connectTimeout");
            }
            if (config.containsKey("queryTimeout")) {
                dbConfig.setQueryTimeout(toInteger(config.get("queryTimeout")));
                updatedFields.add("queryTimeout");
            }
            if (config.containsKey("enabled")) {
                dbConfig.setEnabled(toBoolean(config.get("enabled")));
                updatedFields.add("enabled");
            }
            if (config.containsKey("configFile")) {
                dbConfig.setConfigFile(toString(config.get("configFile")));
                updatedFields.add("configFile");
            }

            // 如果包含databases列表，重新加载配置
            if (config.containsKey("reload") && toBoolean(config.get("reload"))) {
                databaseConfigManager.loadConfig();
                updatedFields.add("databases(reloaded)");
            }

            log.info("数据库配置更新完成: updatedFields={}", updatedFields);
            return buildResult(true, "数据库配置更新成功", Map.of("updatedFields", updatedFields));

        } catch (Exception e) {
            log.error("数据库配置更新失败", e);
            return buildResult(false, "数据库配置更新失败: " + e.getMessage(), null);
        }
    }

    /**
     * 处理系统配置更新
     */
    private Map<String, Object> handleSystemConfig(Map<String, Object> config) {
        log.info("开始更新系统配置");

        List<String> updatedFields = new ArrayList<>();

        try {
            AgentProperties.SystemModule sysConfig = agentProperties.getModules().getSystem();
            if (sysConfig == null) {
                return buildResult(false, "系统模块配置未初始化", null);
            }

            if (config.containsKey("enabled")) {
                sysConfig.setEnabled(toBoolean(config.get("enabled")));
                updatedFields.add("enabled");
            }
            if (config.containsKey("collectInterval")) {
                sysConfig.setCollectInterval(toLong(config.get("collectInterval")));
                updatedFields.add("collectInterval");
            }
            if (config.containsKey("batchSize")) {
                sysConfig.setBatchSize(toInteger(config.get("batchSize")));
                updatedFields.add("batchSize");
            }

            log.info("系统配置更新完成: updatedFields={}", updatedFields);
            return buildResult(true, "系统配置更新成功", Map.of("updatedFields", updatedFields));

        } catch (Exception e) {
            log.error("系统配置更新失败", e);
            return buildResult(false, "系统配置更新失败: " + e.getMessage(), null);
        }
    }

    /**
     * 处理Agent配置更新
     * 通过反射更新AgentProperties中的字段
     */
    private Map<String, Object> handleAgentConfig(Map<String, Object> config) {
        log.info("开始更新Agent配置");

        List<String> updatedFields = new ArrayList<>();
        List<String> failedFields = new ArrayList<>();

        try {
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                try {
                    boolean updated = updateAgentProperty(key, value);
                    if (updated) {
                        updatedFields.add(key);
                    } else {
                        failedFields.add(key);
                    }
                } catch (Exception e) {
                    log.warn("更新Agent配置字段失败: key={}, error={}", key, e.getMessage());
                    failedFields.add(key);
                }
            }

            log.info("Agent配置更新完成: updated={}, failed={}", updatedFields, failedFields);
            Map<String, Object> detail = new HashMap<>();
            detail.put("updatedFields", updatedFields);
            if (!failedFields.isEmpty()) {
                detail.put("failedFields", failedFields);
            }
            return buildResult(true, "Agent配置更新完成", detail);

        } catch (Exception e) {
            log.error("Agent配置更新失败", e);
            return buildResult(false, "Agent配置更新失败: " + e.getMessage(), null);
        }
    }

    /**
     * 通过反射更新AgentProperties中的字段
     */
    private boolean updateAgentProperty(String key, Object value) {
        try {
            // 处理嵌套属性（如 server.host, modules.database.connectPoolSize）
            if (key.contains(".")) {
                return updateNestedProperty(key, value);
            }

            // 顶级属性
            String setterName = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
            Method setter = findSetter(agentProperties.getClass(), setterName, value);
            if (setter != null) {
                setter.invoke(agentProperties, value);
                log.info("更新Agent配置: {} = {}", key, value);
                return true;
            }

            log.warn("未找到Agent配置字段的setter方法: {}", key);
            return false;
        } catch (Exception e) {
            log.warn("更新Agent配置字段失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 更新嵌套属性
     */
    private boolean updateNestedProperty(String key, Object value) throws Exception {
        String[] parts = key.split("\\.");
        Object target = agentProperties;

        // 导航到倒数第二层
        for (int i = 0; i < parts.length - 1; i++) {
            String getterName = "get" + Character.toUpperCase(parts[i].charAt(0)) + parts[i].substring(1);
            Method getter = target.getClass().getMethod(getterName);
            target = getter.invoke(target);
            if (target == null) {
                log.warn("嵌套属性路径中存在null: {}", key);
                return false;
            }
        }

        // 设置最后一层的值
        String lastPart = parts[parts.length - 1];
        String setterName = "set" + Character.toUpperCase(lastPart.charAt(0)) + lastPart.substring(1);
        Method setter = findSetter(target.getClass(), setterName, value);
        if (setter != null) {
            setter.invoke(target, value);
            log.info("更新嵌套Agent配置: {} = {}", key, value);
            return true;
        }

        log.warn("未找到嵌套属性的setter方法: {}", key);
        return false;
    }

    /**
     * 查找匹配的setter方法
     */
    private Method findSetter(Class<?> clazz, String setterName, Object value) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                Class<?> paramType = method.getParameterTypes()[0];
                if (isTypeCompatible(paramType, value)) {
                    return method;
                }
            }
        }
        return null;
    }

    /**
     * 判断参数类型是否兼容
     */
    private boolean isTypeCompatible(Class<?> paramType, Object value) {
        if (value == null) {
            return !paramType.isPrimitive();
        }
        if (paramType.isAssignableFrom(value.getClass())) {
            return true;
        }
        // 基本类型兼容
        if (paramType == Integer.class || paramType == int.class) {
            return value instanceof Number;
        }
        if (paramType == Long.class || paramType == long.class) {
            return value instanceof Number;
        }
        if (paramType == Boolean.class || paramType == boolean.class) {
            return value instanceof Boolean;
        }
        if (paramType == String.class) {
            return true; // 任何值都可以转字符串
        }
        return false;
    }

    /**
     * 构建结果Map
     */
    private Map<String, Object> buildResult(boolean success, String message, Map<String, Object> detail) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", message);
        result.put("timestamp", System.currentTimeMillis());
        if (detail != null) {
            result.put("detail", detail);
        }
        return result;
    }

    // 类型转换辅助方法
    private Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            return Long.parseLong((String) value);
        }
        return null;
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }

    private String toString(Object value) {
        return value != null ? value.toString() : null;
    }
}
