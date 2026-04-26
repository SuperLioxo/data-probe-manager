package com.lixin.probe.service;

import com.lixin.probe.entity.DatabaseConnection;

import java.util.List;

/**
 * 数据库连接Service
 *
 * @author Claude Code
@ -date 2026-03-26
 */
public interface DatabaseConnectionService {

    /**
     * 获取所有数据库连接列表
     */
    List<DatabaseConnection> getAllConnections();

    /**
     * 根据数据库类型获取连接列表
     */
    List<DatabaseConnection> getConnectionsByType(String databaseType);

    /**
     * 根据ID获取连接
     */
    DatabaseConnection getConnectionById(Long id);

    /**
     * 创建连接
     */
    DatabaseConnection createConnection(DatabaseConnection connection);

    /**
     * 更新连接
     */
    void updateConnection(DatabaseConnection connection);

    /**
     * 删除连接
     */
    void deleteConnection(Long id);

    /**
     * 测试连接
     */
    boolean testConnection(DatabaseConnection connection);
}
