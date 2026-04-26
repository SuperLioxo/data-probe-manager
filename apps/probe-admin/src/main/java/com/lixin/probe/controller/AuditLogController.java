package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.service.AuditLogService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ExcelExportUtil;
import com.lixin.probe.util.ValidationUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志Controller
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditLogController.class);

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 分页查询审计日志
     */
    @GetMapping
    public Result<Page<com.lixin.probe.entity.AuditLog>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String module) {

        Result<Void> error = ValidationUtil.validatePageParams(pageNum, pageSize);
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(() -> {
            Page<com.lixin.probe.entity.AuditLog> page = new Page<>(pageNum, pageSize);
            return auditLogService.getLogs(page, userId, operation, module);
        }, "查询审计日志失败");
    }

    /**
     * 根据ID查询审计日志详情
     */
    @GetMapping("/{id}")
    public Result<com.lixin.probe.entity.AuditLog> getById(@PathVariable Long id) {
        Result<Void> error = ValidationUtil.validateId(id, "审计日志ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(() -> {
            com.lixin.probe.entity.AuditLog auditLog = auditLogService.getById(id);
            if (auditLog == null) {
                throw new IllegalArgumentException("日志不存在");
            }
            return auditLog;
        }, "查询审计日志详情失败");
    }

    /**
     * 导出审计日志为Excel
     */
    @GetMapping("/export")
    public void exportAuditLogs(
            HttpServletResponse response,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String module) {
        try {
            // 获取所有数据（使用大页面）
            Page<com.lixin.probe.entity.AuditLog> page = new Page<>(1, 10000);
            Page<com.lixin.probe.entity.AuditLog> result = auditLogService.getLogs(page, userId, operation, module);
            List<com.lixin.probe.entity.AuditLog> logs = result.getRecords();

            // 生成Excel
            byte[] excelBytes = ExcelExportUtil.exportAuditLogs(logs);

            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String filename = URLEncoder.encode("审计日志.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);

            // 使用try-with-resources确保OutputStream正确关闭
            try (var outputStream = response.getOutputStream()) {
                outputStream.write(excelBytes);
                outputStream.flush();
            }

            log.info("导出审计日志成功，共{}条记录", logs.size());
        } catch (Exception e) {
            log.error("导出审计日志失败", e);
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }

    /**
     * 高级搜索审计日志
     */
    @GetMapping("/search")
    public Result<Page<com.lixin.probe.entity.AuditLog>> search(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String keyword) {

        Result<Void> error = ValidationUtil.validatePageParams(pageNum, pageSize);
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(() -> {
            Page<com.lixin.probe.entity.AuditLog> page = new Page<>(pageNum, pageSize);

            LocalDateTime start = startTime != null ? LocalDateTime.parse(startTime) : null;
            LocalDateTime end = endTime != null ? LocalDateTime.parse(endTime) : null;

            return auditLogService.searchLogs(page, userId, operation, module,
                    level, start, end, keyword);
        }, "搜索审计日志失败");
    }

    /**
     * 获取日志统计信息
     */
    @GetMapping("/statistics")
    public Result<java.util.Map<String, Object>> getStatistics(
            @RequestParam String startTime,
            @RequestParam String endTime) {

        return ControllerHelper.safeGet(() -> {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);

            return auditLogService.getStatistics(start, end);
        }, "获取统计信息失败");
    }

    /**
     * 获取用户操作日志
     */
    @GetMapping("/user/{userId}")
    public Result<java.util.List<com.lixin.probe.entity.AuditLog>> getUserLogs(
            @PathVariable String userId,
            @RequestParam(defaultValue = "50") Integer limit) {

        return ControllerHelper.safeGet(() -> {
            return auditLogService.getLogsByUser(userId, limit);
        }, "获取用户日志失败");
    }

    /**
     * 获取业务实体操作日志
     */
    @GetMapping("/business/{businessType}/{businessId}")
    public Result<java.util.List<com.lixin.probe.entity.AuditLog>> getBusinessLogs(
            @PathVariable String businessType,
            @PathVariable Long businessId) {

        return ControllerHelper.safeGet(() -> {
            return auditLogService.getLogsByBusiness(businessType, businessId);
        }, "获取业务日志失败");
    }
}
