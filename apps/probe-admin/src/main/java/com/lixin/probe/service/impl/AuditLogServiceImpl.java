package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lixin.probe.config.AuditLogProperties;
import com.lixin.probe.entity.AuditLog;
import com.lixin.probe.enums.AuditLogLevel;
import com.lixin.probe.enums.AuditLogOperation;
import com.lixin.probe.mapper.AuditLogMapper;
import com.lixin.probe.service.AuditLogService;
import com.lixin.probe.util.ExcelExportUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审计日志服务实现类
 */
@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    @Autowired
    private AuditLogProperties properties;

    @Override
    public Page<AuditLog> getLogs(Page<AuditLog> page, String userId, String operation, String module) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();

        if (userId != null && !userId.isEmpty()) {
            wrapper.eq(AuditLog::getUserId, userId);
        }
        if (operation != null && !operation.isEmpty()) {
            wrapper.eq(AuditLog::getOperation, operation);
        }
        if (module != null && !module.isEmpty()) {
            wrapper.eq(AuditLog::getModule, module);
        }

        // 只查询未归档的日志
        wrapper.eq(AuditLog::getIsArchived, false);

        // 先查询总数（不带ORDER BY）
        LambdaQueryWrapper<AuditLog> countWrapper = wrapper.clone();
        Long total = this.count(countWrapper);
        page.setTotal(total);

        // 添加排序（只在数据查询时使用）
        wrapper.orderByDesc(AuditLog::getCreateTime);

        // 执行分页查询
        Page<AuditLog> result = this.page(page, wrapper);

        // 确保total正确设置
        if (result.getTotal() == 0 && total > 0) {
            result.setTotal(total);
        }

        log.debug("[AuditLog] 查询审计日志 - total: {}, records: {}", result.getTotal(), result.getRecords().size());

        return result;
    }

    @Override
    public Page<AuditLog> searchLogs(Page<AuditLog> page, String userId, String operation,
                                     String module, String level, LocalDateTime startTime,
                                     LocalDateTime endTime, String keyword) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();

        if (userId != null && !userId.isEmpty()) {
            wrapper.eq(AuditLog::getUserId, userId);
        }
        if (operation != null && !operation.isEmpty()) {
            wrapper.eq(AuditLog::getOperation, operation);
        }
        if (module != null && !module.isEmpty()) {
            wrapper.eq(AuditLog::getModule, module);
        }
        if (level != null && !level.isEmpty()) {
            wrapper.eq(AuditLog::getLevel, level);
        }
        if (startTime != null) {
            wrapper.ge(AuditLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(AuditLog::getCreateTime, endTime);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(AuditLog::getDescription, keyword)
                    .or().like(AuditLog::getUsername, keyword)
                    .or().like(AuditLog::getModule, keyword));
        }

        // 只查询未归档的日志
        wrapper.eq(AuditLog::getIsArchived, false);

        // 按创建时间倒序
        wrapper.orderByDesc(AuditLog::getCreateTime);

        return this.page(page, wrapper);
    }

    @Override
    public boolean create(AuditLog auditLog) {
        if (!properties.isEnabled()) {
            return true;
        }
        return this.save(auditLog);
    }

    @Override
    @Async
    public void createAsync(AuditLog auditLog) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            this.save(auditLog);
        } catch (Exception e) {
            log.error("异步保存审计日志失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void recordLogin(String userId, String username, String ipAddress,
                           String userAgent, boolean success) {
        if (!properties.isEnabled()) {
            return;
        }

        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .username(username)
                .operation(AuditLogOperation.LOGIN.getCode())
                .module("AUTH")
                .description(success ? "用户登录成功" : "用户登录失败")
                .level(success ? AuditLogLevel.INFO.name() : AuditLogLevel.WARN.name())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .responseCode(success ? 200 : 401)
                .isException(!success)
                .build();

        if (properties.isAsync()) {
            createAsync(auditLog);
        } else {
            create(auditLog);
        }
    }

    @Override
    public void recordLogout(String userId, String username) {
        if (!properties.isEnabled()) {
            return;
        }

        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .username(username)
                .operation(AuditLogOperation.LOGOUT.getCode())
                .module("AUTH")
                .description("用户登出")
                .level(AuditLogLevel.INFO.name())
                .build();

        if (properties.isAsync()) {
            createAsync(auditLog);
        } else {
            create(auditLog);
        }
    }

    @Override
    public void recordPermissionChange(String userId, String username, Long targetUserId,
                                       String oldPermissions, String newPermissions) {
        if (!properties.isEnabled()) {
            return;
        }

        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .username(username)
                .operation(AuditLogOperation.PERMISSION_CHANGE.getCode())
                .module("AUTH")
                .description("权限变更: 用户ID=" + targetUserId)
                .level(AuditLogLevel.CRITICAL.name())
                .businessId(targetUserId)
                .businessType("USER")
                .requestParams(String.format("{\"oldPermissions\":\"%s\",\"newPermissions\":\"%s\"}",
                        oldPermissions, newPermissions))
                .build();

        if (properties.isAsync()) {
            createAsync(auditLog);
        } else {
            create(auditLog);
        }
    }

    @Override
    public void recordConfigChange(String userId, String username, String configKey,
                                   String oldValue, String newValue) {
        if (!properties.isEnabled()) {
            return;
        }

        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .username(username)
                .operation(AuditLogOperation.CONFIG_CHANGE.getCode())
                .module("CONFIG")
                .description("配置变更: " + configKey)
                .level(AuditLogLevel.WARN.name())
                .requestParams(String.format("{\"configKey\":\"%s\",\"oldValue\":\"%s\",\"newValue\":\"%s\"}",
                        configKey, oldValue, newValue))
                .build();

        if (properties.isAsync()) {
            createAsync(auditLog);
        } else {
            create(auditLog);
        }
    }

    @Override
    public List<AuditLog> getLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(AuditLog::getCreateTime, startTime)
                .le(AuditLog::getCreateTime, endTime)
                .eq(AuditLog::getIsArchived, false)
                .orderByDesc(AuditLog::getCreateTime);

        return this.list(wrapper);
    }

    @Override
    public List<AuditLog> getLogsByUser(String userId, int limit) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuditLog::getUserId, userId)
                .eq(AuditLog::getIsArchived, false)
                .orderByDesc(AuditLog::getCreateTime)
                .last("LIMIT " + limit);

        return this.list(wrapper);
    }

    @Override
    public List<AuditLog> getLogsByBusiness(String businessType, Long businessId) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuditLog::getBusinessType, businessType)
                .eq(AuditLog::getBusinessId, businessId)
                .eq(AuditLog::getIsArchived, false)
                .orderByDesc(AuditLog::getCreateTime);

        return this.list(wrapper);
    }

    @Override
    public Map<String, Long> countByOperation(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(AuditLog::getCreateTime, startTime)
                .le(AuditLog::getCreateTime, endTime)
                .eq(AuditLog::getIsArchived, false);

        List<AuditLog> logs = this.list(wrapper);

        return logs.stream()
                .collect(Collectors.groupingBy(AuditLog::getOperation, Collectors.counting()));
    }

    @Override
    public Map<String, Long> countByUser(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(AuditLog::getCreateTime, startTime)
                .le(AuditLog::getCreateTime, endTime)
                .eq(AuditLog::getIsArchived, false);

        List<AuditLog> logs = this.list(wrapper);

        return logs.stream()
                .collect(Collectors.groupingBy(AuditLog::getUsername, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredLogs(LocalDateTime beforeDate) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(AuditLog::getCreateTime, beforeDate)
                .eq(AuditLog::getIsArchived, false);

        int count = Math.toIntExact(this.count(wrapper));

        if (count > 0) {
            this.remove(wrapper);
            log.info("清理了 {} 条过期审计日志，清理日期: {}", count, beforeDate);
        }

        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveLogs(LocalDateTime beforeDate) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(AuditLog::getCreateTime, beforeDate)
                .eq(AuditLog::getIsArchived, false);

        List<AuditLog> logs = this.list(wrapper);

        if (logs.isEmpty()) {
            return 0;
        }

        // 标记为已归档
        logs.forEach(log -> {
            log.setIsArchived(true);
            log.setArchiveTime(LocalDateTime.now());
        });

        this.updateBatchById(logs);

        log.info("归档了 {} 条审计日志，归档日期: {}", logs.size(), beforeDate);

        return logs.size();
    }

    @Override
    public byte[] exportToExcel(List<AuditLog> logs) {
        return ExcelExportUtil.exportAuditLogs(logs);
    }

    @Override
    public Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(AuditLog::getCreateTime, startTime)
                .le(AuditLog::getCreateTime, endTime)
                .eq(AuditLog::getIsArchived, false);

        List<AuditLog> logs = this.list(wrapper);

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", logs.size());
        stats.put("byOperation", countByOperation(startTime, endTime));
        stats.put("byUser", countByUser(startTime, endTime, 10));
        stats.put("byLevel", logs.stream()
                .collect(Collectors.groupingBy(AuditLog::getLevel, Collectors.counting())));
        stats.put("exceptionCount", logs.stream().filter(AuditLog::getIsException).count());

        return stats;
    }
}
