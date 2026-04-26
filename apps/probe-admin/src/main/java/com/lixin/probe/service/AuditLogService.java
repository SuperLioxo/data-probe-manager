package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lixin.probe.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务接口
 */
public interface AuditLogService extends IService<AuditLog> {

    /**
     * 分页查询审计日志
     * @param page 分页参数
     * @param userId 用户ID（可选）
     * @param operation 操作类型（可选）
     * @param module 模块名称（可选）
     * @return 审计日志分页结果
     */
    Page<AuditLog> getLogs(Page<AuditLog> page, String userId, String operation, String module);

    /**
     * 高级查询审计日志
     * @param page 分页参数
     * @param userId 用户ID（可选）
     * @param operation 操作类型（可选）
     * @param module 模块名称（可选）
     * @param level 日志级别（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param keyword 关键词（可选）
     * @return 审计日志分页结果
     */
    Page<AuditLog> searchLogs(Page<AuditLog> page, String userId, String operation,
                              String module, String level, LocalDateTime startTime,
                              LocalDateTime endTime, String keyword);

    /**
     * 创建审计日志
     * @param auditLog 审计日志
     * @return 是否成功
     */
    boolean create(AuditLog auditLog);

    /**
     * 异步创建审计日志
     * @param auditLog 审计日志
     */
    void createAsync(AuditLog auditLog);

    /**
     * 记录登录日志
     * @param userId 用户ID
     * @param username 用户名
     * @param ipAddress IP地址
     * @param userAgent 用户代理
     * @param success 是否成功
     */
    void recordLogin(String userId, String username, String ipAddress,
                     String userAgent, boolean success);

    /**
     * 记录登出日志
     * @param userId 用户ID
     * @param username 用户名
     */
    void recordLogout(String userId, String username);

    /**
     * 记录权限变更日志
     * @param userId 操作用户ID
     * @param username 操作用户名
     * @param targetUserId 目标用户ID
     * @param oldPermissions 旧权限
     * @param newPermissions 新权限
     */
    void recordPermissionChange(String userId, String username, Long targetUserId,
                                String oldPermissions, String newPermissions);

    /**
     * 记录配置变更日志
     * @param userId 操作用户ID
     * @param username 操作用户名
     * @param configKey 配置键
     * @param oldValue 旧值
     * @param newValue 新值
     */
    void recordConfigChange(String userId, String username, String configKey,
                           String oldValue, String newValue);

    /**
     * 根据时间范围查询日志
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日志列表
     */
    List<AuditLog> getLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据用户查询日志
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 日志列表
     */
    List<AuditLog> getLogsByUser(String userId, int limit);

    /**
     * 根据业务实体查询日志
     * @param businessType 业务类型
     * @param businessId 业务ID
     * @return 日志列表
     */
    List<AuditLog> getLogsByBusiness(String businessType, Long businessId);

    /**
     * 统计日志数量（按操作类型）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 统计结果
     */
    Map<String, Long> countByOperation(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计日志数量（按用户）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param limit 限制数量
     * @return 统计结果
     */
    Map<String, Long> countByUser(LocalDateTime startTime, LocalDateTime endTime, int limit);

    /**
     * 清理过期日志
     * @param beforeDate 清除此日期之前的日志
     * @return 清理的日志数量
     */
    int cleanupExpiredLogs(LocalDateTime beforeDate);

    /**
     * 归档日志
     * @param beforeDate 归档此日期之前的日志
     * @return 归档的日志数量
     */
    int archiveLogs(LocalDateTime beforeDate);

    /**
     * 导出日志为Excel
     * @param logs 日志列表
     * @return Excel文件字节数组
     */
    byte[] exportToExcel(List<AuditLog> logs);

    /**
     * 获取日志统计信息
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 统计信息
     */
    Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime);
}
