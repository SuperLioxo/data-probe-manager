package com.lixin.probe.agent.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 数据库模块工厂实现
 *
 * @author Claude Code
 * @since 2.0
 */
@Component
public class DatabaseModuleFactoryImpl implements DatabaseModuleFactory {

    private static final Logger log = LoggerFactory.getLogger(DatabaseModuleFactoryImpl.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public DatabaseModule createModule(String probeKey) {
        try {
            log.info("创建DatabaseModule实例: probeKey={}", probeKey);

            // 通过Spring容器创建新的实例，自动注入依赖
            DatabaseModule module = applicationContext.getBean(DatabaseModule.class);

            log.info("DatabaseModule实例创建成功: probeKey={}", probeKey);
            return module;

        } catch (Exception e) {
            log.error("创建DatabaseModule实例失败: probeKey={}", probeKey, e);
            return null;
        }
    }
}
