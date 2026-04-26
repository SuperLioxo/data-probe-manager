package com.lixin.probe.factory;

import com.lixin.probe.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 探针工厂管理器
 * 根据探针类型选择合适的工厂
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
public class ProbeFactoryManager {

    private static final Logger log = LoggerFactory.getLogger(ProbeFactoryManager.class);

    private final Map<String, ProbeFactory> factories = new HashMap<>();

    /**
     * 构造函数，自动注入所有ProbeFactory实现
     */
    @Autowired
    public ProbeFactoryManager(List<ProbeFactory> factoryList) {
        for (ProbeFactory factory : factoryList) {
            String type = factory.getSupportedType();
            factories.put(type, factory);
            log.info("注册探针工厂: type={}, factory={}", type, factory.getClass().getSimpleName());
        }
    }

    /**
     * 获取指定类型的工厂
     *
     * @param type 探针类型（FILE, DATABASE等）
     * @return 工厂实例
     * @throws IllegalArgumentException 如果类型不支持
     */
    public ProbeFactory getFactory(String type) {
        ProbeFactory factory = factories.get(type);
        if (factory == null) {
            throw new IllegalArgumentException(
                "不支持的探针类型: " + type + "，支持的类型: " + getSupportedTypes()
            );
        }
        return factory;
    }

    /**
     * 检查探针类型是否支持
     *
     * @param type 探针类型
     * @return true如果支持
     */
    public boolean isSupportedType(String type) {
        return factories.containsKey(type);
    }

    /**
     * 获取所有支持的探针类型
     *
     * @return 类型列表
     */
    public List<String> getSupportedTypes() {
        return List.copyOf(factories.keySet());
    }

    /**
     * 获取已注册的工厂数量
     *
     * @return 工厂数量
     */
    public int getFactoryCount() {
        return factories.size();
    }
}
