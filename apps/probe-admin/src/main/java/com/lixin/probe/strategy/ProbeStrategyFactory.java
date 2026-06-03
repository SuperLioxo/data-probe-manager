package com.lixin.probe.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 探针策略工厂
 * 根据探针类型返回对应的策略
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
public class ProbeStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(ProbeStrategyFactory.class);

    private final Map<String, ProbeHeartbeatStrategy> heartbeatStrategies = new HashMap<>();

    /**
     * 构造函数，自动注入所有策略实现
     */
    @Autowired
    public ProbeStrategyFactory(List<ProbeHeartbeatStrategy> strategyList) {
        for (ProbeHeartbeatStrategy strategy : strategyList) {
            String type = strategy.getSupportedType();
            heartbeatStrategies.put(type, strategy);
            log.info("注册心跳策略: type={}, strategy={}", type, strategy.getClass().getSimpleName());
        }
    }

    /**
     * 获取心跳策略
     *
     * @param probeType 探针类型
     * @return 心跳策略实例
     */
    public ProbeHeartbeatStrategy getHeartbeatStrategy(String probeType) {
        ProbeHeartbeatStrategy strategy = heartbeatStrategies.get(probeType);

        if (strategy == null) {
            log.debug("未找到探针类型的心跳策略: {}, 使用默认策略", probeType);
            // 返回默认策略（数据库探针策略作为默认）
            return heartbeatStrategies.values().stream()
                    .findFirst()
                    .orElse(null);
        }

        return strategy;
    }

    /**
     * 检查探针类型是否支持
     *
     * @param probeType 探针类型
     * @return true如果支持
     */
    public boolean isSupportedType(String probeType) {
        return heartbeatStrategies.containsKey(probeType);
    }

    /**
     * 获取所有支持的探针类型
     *
     * @return 类型列表
     */
    public List<String> getSupportedTypes() {
        return List.copyOf(heartbeatStrategies.keySet());
    }
}
