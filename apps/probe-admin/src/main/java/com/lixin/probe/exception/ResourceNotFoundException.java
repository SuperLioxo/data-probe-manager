package com.lixin.probe.exception;

/**
 * 资源未找到异常
 * 当请求的资源不存在时抛出此异常
 *
 * @author Claude Code
 * @date 2026-04-12
 */
public class ResourceNotFoundException extends BusinessException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(404, String.format("%s不存在: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String resourceType, Long resourceId) {
        this(resourceType, String.valueOf(resourceId));
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
