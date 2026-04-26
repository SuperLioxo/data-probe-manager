package com.lixin.probe.agent.module;

/**
 * 文件模块工厂
 *
 * @author Claude Code
 * @since 2.0
 */
public interface FileModuleFactory {

    /**
     * 为指定探针创建FileModule实例
     *
     * @param probeKey 探针标识
     * @return FileModule实例
     */
    FileModule createModule(String probeKey);
}
