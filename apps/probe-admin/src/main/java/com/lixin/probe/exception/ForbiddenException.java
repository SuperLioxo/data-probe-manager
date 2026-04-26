package com.lixin.probe.exception;

/**
 * 禁止访问异常
 * 当用户已登录但权限不足时抛出此异常
 *
 * @author Claude Code
 * @date 2026-04-12
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(403, "权限不足，无法访问该资源");
    }

    public ForbiddenException(String message) {
        super(403, message);
    }

    public ForbiddenException(String resource, String action) {
        super(403, String.format("权限不足: 无法%s %s", action, resource));
    }
}
