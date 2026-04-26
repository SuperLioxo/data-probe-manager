package com.lixin.probe.exception;

/**
 * 权限不足异常
 *
 * @author Claude Code
 * @date 2026-03-11
 */
public class PermissionDeniedException extends BusinessException {

    public PermissionDeniedException() {
        super(ErrorCode.PERMISSION_DENIED.getCode(),
              ErrorCode.PERMISSION_DENIED.getMessage());
    }

    public PermissionDeniedException(String resource) {
        super(ErrorCode.PERMISSION_DENIED.getCode(),
              "权限不足: 无权访问 " + resource);
    }

    public PermissionDeniedException(String resource, String operation) {
        super(ErrorCode.PERMISSION_DENIED.getCode(),
              String.format("权限不足: 无权%s %s", operation, resource));
    }
}
