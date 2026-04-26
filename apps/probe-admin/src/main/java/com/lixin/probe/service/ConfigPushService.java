package com.lixin.probe.service;

import java.util.Map;

/**
 * 配置推送服务接口
 * 负责向Agent推送配置热更新命令
 *
 * @author Claude Code
 * @since 1.0
 */
public interface ConfigPushService {

    /**
     * 向指定Agent推送配置更新
     *
     * @param agentCode  Agent代码
     * @param configType 配置类型 (database/system/agent)
     * @param config     配置内容
     * @return 推送结果
     */
    Map<String, Object> pushConfig(String agentCode, String configType, Map<String, Object> config);

    /**
     * 向所有在线Agent推送配置更新
     *
     * @param configType 配置类型 (database/system/agent)
     * @param config     配置内容
     * @return 推送结果
     */
    Map<String, Object> pushConfigToAll(String configType, Map<String, Object> config);
}
