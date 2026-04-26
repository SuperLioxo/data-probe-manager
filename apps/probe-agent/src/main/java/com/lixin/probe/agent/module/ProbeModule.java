package com.lixin.probe.agent.module;

import com.lixin.probe.agent.constant.Command;

/**
 * 探针模块接口
 * 所有探针模块都需要实现此接口
 */
public interface ProbeModule {

    /**
     * 获取模块名称
     */
    String getName();

    /**
     * 获取探针类型
     */
    ProbeType getType();

    /**
     * 检查模块是否启用
     */
    boolean isEnabled();

    /**
     * 启动模块
     */
    void start() throws Exception;

    /**
     * 停止模块
     */
    void stop() throws Exception;

    /**
     * 获取模块状态
     */
    ModuleStatus getStatus();

    /**
     * 处理命令
     *
     * @param command 命令类型
     * @param payload 命令载荷
     */
    void onCommand(Command command, Object payload);
}
