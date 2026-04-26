package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lixin.probe.enums.AuditLogLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志实体
 */
@NoArgsConstructor
@AllArgsConstructor
@TableName("audit_log")
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 操作类型：CREATE, UPDATE, DELETE, QUERY, LOGIN, LOGOUT等
     */
    private String operation;

    /**
     * 资源类型
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * 操作详情
     */
    private String details;

    /**
     * 操作描述
     */
    private String description;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 状态
     */
    private String status;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 日志级别：INFO, WARN, ERROR, CRITICAL
     */
    private String level;

    /**
     * 业务ID（关联的业务实体ID）
     */
    private Long businessId;

    /**
     * 业务类型（关联的业务实体类型）
     */
    private String businessType;

    /**
     * 响应消息
     */
    private String responseMessage;

    /**
     * 是否异常
     */
    private Boolean isException;

    /**
     * 异常消息
     */
    private String exceptionMessage;

    /**
     * 是否已归档
     */
    private Boolean isArchived;

    /**
     * 归档时间
     */
    private LocalDateTime archiveTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 请求方法
     */
    private String requestMethod;

    /**
     * 方法签名
     */
    private String method;

    /**
     * 请求参数（JSON）
     */
    private String requestParams;

    /**
     * 响应状态码
     */
    private Integer responseCode;

    /**
     * 执行时间（毫秒）
     */
    private Long executionTime;

    /**
     * 模块名称
     */
    private String module;

    // Getter and Setter methods
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }

    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }

    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }

    public Boolean getIsException() { return isException; }
    public void setIsException(Boolean isException) { this.isException = isException; }

    public String getExceptionMessage() { return exceptionMessage; }
    public void setExceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; }

    public Boolean getIsArchived() { return isArchived; }
    public void setIsArchived(Boolean isArchived) { this.isArchived = isArchived; }

    public LocalDateTime getArchiveTime() { return archiveTime; }
    public void setArchiveTime(LocalDateTime archiveTime) { this.archiveTime = archiveTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }

    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getRequestParams() { return requestParams; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }

    public Integer getResponseCode() { return responseCode; }
    public void setResponseCode(Integer responseCode) { this.responseCode = responseCode; }

    public Long getExecutionTime() { return executionTime; }
    public void setExecutionTime(Long executionTime) { this.executionTime = executionTime; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    /**
     * Builder pattern for AuditLog
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String userId;
        private String username;
        private String operation;
        private String resourceType;
        private String resourceId;
        private String details;
        private String description;
        private String ipAddress;
        private String userAgent;
        private String status;
        private String errorMessage;
        private String level;
        private Long businessId;
        private String businessType;
        private String responseMessage;
        private Boolean isException;
        private String exceptionMessage;
        private Boolean isArchived;
        private LocalDateTime archiveTime;
        private LocalDateTime createTime;
        private String requestUrl;
        private String requestMethod;
        private String method;
        private String requestParams;
        private Integer responseCode;
        private Long executionTime;
        private String module;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder operation(String operation) { this.operation = operation; return this; }
        public Builder resourceType(String resourceType) { this.resourceType = resourceType; return this; }
        public Builder resourceId(String resourceId) { this.resourceId = resourceId; return this; }
        public Builder details(String details) { this.details = details; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder level(String level) { this.level = level; return this; }
        public Builder level(AuditLogLevel level) { this.level = level.name(); return this; }
        public Builder businessId(Long businessId) { this.businessId = businessId; return this; }
        public Builder businessType(String businessType) { this.businessType = businessType; return this; }
        public Builder responseMessage(String responseMessage) { this.responseMessage = responseMessage; return this; }
        public Builder isException(Boolean isException) { this.isException = isException; return this; }
        public Builder exceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; return this; }
        public Builder isArchived(Boolean isArchived) { this.isArchived = isArchived; return this; }
        public Builder archiveTime(LocalDateTime archiveTime) { this.archiveTime = archiveTime; return this; }
        public Builder createTime(LocalDateTime createTime) { this.createTime = createTime; return this; }
        public Builder requestUrl(String requestUrl) { this.requestUrl = requestUrl; return this; }
        public Builder requestMethod(String requestMethod) { this.requestMethod = requestMethod; return this; }
        public Builder method(String method) { this.method = method; return this; }
        public Builder requestParams(String requestParams) { this.requestParams = requestParams; return this; }
        public Builder responseCode(Integer responseCode) { this.responseCode = responseCode; return this; }
        public Builder executionTime(Long executionTime) { this.executionTime = executionTime; return this; }
        public Builder module(String module) { this.module = module; return this; }

        public AuditLog build() {
            AuditLog log = new AuditLog();
            log.id = this.id;
            log.userId = this.userId;
            log.username = this.username;
            log.operation = this.operation;
            log.resourceType = this.resourceType;
            log.resourceId = this.resourceId;
            log.details = this.details;
            log.description = this.description;
            log.ipAddress = this.ipAddress;
            log.userAgent = this.userAgent;
            log.status = this.status;
            log.errorMessage = this.errorMessage;
            log.level = this.level != null ? this.level : AuditLogLevel.INFO.name();
            log.businessId = this.businessId;
            log.businessType = this.businessType;
            log.responseMessage = this.responseMessage;
            log.isException = this.isException != null ? this.isException : false;
            log.exceptionMessage = this.exceptionMessage;
            log.isArchived = this.isArchived != null ? this.isArchived : false;
            log.archiveTime = this.archiveTime;
            log.createTime = this.createTime != null ? this.createTime : LocalDateTime.now();
            log.requestUrl = this.requestUrl;
            log.requestMethod = this.requestMethod;
            log.method = this.method;
            log.requestParams = this.requestParams;
            log.responseCode = this.responseCode;
            log.executionTime = this.executionTime;
            log.module = this.module;
            return log;
        }
    }
}
