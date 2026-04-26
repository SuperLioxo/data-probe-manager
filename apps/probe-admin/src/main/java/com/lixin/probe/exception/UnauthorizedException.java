package com.lixin.probe.exception;

/**
 * 未授权异常
 * 当用户未登录或Token无效时抛出此异常
 *
 * @author Claude Code
 * @date 2026-04-12
 */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException() {
        super(401, "未授权访问，请先登录");
    }

    public UnauthorizedException(String message) {
        super(401, message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(401, message, cause);
    }
}
