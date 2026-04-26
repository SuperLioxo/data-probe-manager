package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.SecurityEvent;

import java.time.LocalDateTime;

/**
 * 安全事件Service接口
 */
public interface SecurityEventService {

    /**
     * 记录安全事件
     * @param eventType 事件类型
     * @param probeKey 探针标识
     * @param ipAddress IP地址
     * @param severity 严重程度
     * @param details 事件详情
     */
    void logSecurityEvent(String eventType, String probeKey, String ipAddress,
                          String severity, String details);

    /**
     * 分页查询安全事件
     */
    Page<SecurityEvent> getPage(int pageNum, int pageSize, String eventType,
                               String severity, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据ID查询事件
     */
    SecurityEvent getById(Long id);

    /**
     * 更新事件状态
     */
    void updateStatus(Long id, String status, String notes);

    /**
     * 删除指定时间之前的事件
     */
    void deleteBefore(LocalDateTime time);

    /**
     * 获取事件统计
     */
    java.util.Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime);
}
