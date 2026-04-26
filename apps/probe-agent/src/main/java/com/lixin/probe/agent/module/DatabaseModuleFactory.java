package com.lixin.probe.agent.module;

/**
 * 数据库模块工厂
 *
 * @author Claude Code
 * @since 2.0
 */
public interface DatabaseModuleFactory {

    /**
     * 为指定探针创建DatabaseModule实例
     *
     * @param probeKey 探针标识
     * @return DatabaseModule实例
     */
    DatabaseModule createModule(String probeKey);
}
