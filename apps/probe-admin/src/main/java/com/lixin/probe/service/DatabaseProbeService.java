package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.DatabaseProbe;

import java.util.List;

/**
 * 数据库探针Service接口
 *
 * @author Claude Code
 * @date 2026-03-26
 */
public interface DatabaseProbeService {

    /**
     * 查询所有数据库探针
     */
    List<DatabaseProbe> list();

    /**
     * 分页查询数据库探针
     */
    Page<DatabaseProbe> getPage(int pageNum, int pageSize, String name, String status);

    /**
     * 根据ID查询数据库探针
     */
    DatabaseProbe getById(Long id);

    /**
     * 根据probeKey查询数据库探针
     */
    DatabaseProbe getByProbeKey(String probeKey);

    /**
     * 创建数据库探针
     * 包含密码加密和连接测试
     */
    DatabaseProbe create(DatabaseProbe databaseProbe);

    /**
     * 更新数据库探针
     */
    void update(DatabaseProbe databaseProbe);

    /**
     * 删除数据库探针
     */
    void delete(Long id);

    /**
     * 测试数据库连接
     * 在保存之前测试连接是否可用
     */
    boolean testConnection(DatabaseProbe databaseProbe);

    /**
     * 同步配置到Agent
     * 通过WebSocket发送UPDATE_DB_CONFIG命令
     */
    void syncConfigToAgent(String probeKey);

    /**
     * 加密密码
     * 在存储到数据库之前加密
     */
    void encryptPassword(DatabaseProbe databaseProbe);

    /**
     * 解密密码
     * 用于同步到Agent时使用
     */
    String decryptPassword(String encryptedPassword);

    /**
     * 更新心跳时间
     */
    void updateHeartbeat(String probeKey);

    /**
     * 更新采集时间
     */
    void updateCollectTime(String probeKey);

    /**
     * 根据数据库类型和主机查询探针列表
     */
    List<DatabaseProbe> getByDatabaseTypeAndHost(String databaseType, String host);

    /**
     * 切换探针的数据库连接
     *
     * @param probeKey 探针标识
     * @param connectionId 数据库连接ID
     * @return 切换后的数据库连接信息
     */
    com.lixin.probe.dto.DatabaseConnectionDTO switchConnection(String probeKey, Long connectionId);
}
