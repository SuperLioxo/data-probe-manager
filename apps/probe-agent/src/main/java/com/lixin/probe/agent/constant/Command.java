package com.lixin.probe.agent.constant;


/**
 * WebSocket 命令枚举
 */
public enum Command {

    /**
     * 数据库探查
     */
    PROBE,

    /**
     * 推送探查结果
     */
    PROBE_PUSH,

    /**
     * 文件探查
     */
    FILE_PROBE,

    /**
     * 推送文件探查结果
     */
    FILE_PROBE_PUSH,

    /**
     * 数据提取
     */
    EXTRACT,

    /**
     * 文件数据传输
     */
    FILE_DATA_PROBE,

    /**
     * MinIO 文件上传
     */
    MINIO_FILE,

    /**
     * 表数据导出
     */
    TABLE_DATA,

    /**
     * 资源监控请求
     */
    MONITOR_REQUEST,

    /**
     * 心跳
     */
    HEARTBEAT,

    /**
     * 启动探针
     */
    START,

    /**
     * 停止探针
     */
    STOP,

    /**
     * 重启探针
     */
    RESTART,

    /**
     * 更新数据库配置
     */
    UPDATE_DB_CONFIG,

    /**
     * 配置热更新
     */
    CONFIG_UPDATE,

    /**
     * 优雅关闭Agent
     */
    SHUTDOWN,

    /**
     * 日志上传
     */
    LOG_UPLOAD,

    /**
     * Agent升级
     */
    UPGRADE;

    /**
     * 消息类型
     */
    public enum Type {
        REQUEST,
        RESPONSE,
        NOTIFY
    }
}
