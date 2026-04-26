package com.lixin.probe.exception;

import lombok.Getter;

/**
 * 业务异常类
 * 用于在业务逻辑中主动抛出的异常
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;

    public BusinessException(String message) {
        this(500, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(String message, Throwable cause) {
        this(500, message, cause);
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }

    /**
     * 参数无效异常 (400)
     */
    public static BusinessException invalidParam(String message) {
        return new BusinessException(400, "参数错误: " + message);
    }

    /**
     * 资源未找到异常 (404)
     */
    public static BusinessException notFound(String resourceName) {
        return new BusinessException(404, resourceName + "不存在");
    }

    /**
     * 权限不足异常 (403)
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(403, "权限不足: " + message);
    }

    /**
     * 未授权异常 (401)
     */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, "未授权: " + message);
    }

    /**
     * 资源冲突异常 (409)
     */
    public static BusinessException conflict(String message) {
        return new BusinessException(409, "资源冲突: " + message);
    }

    /**
     * 系统错误异常 (500)
     */
    public static BusinessException systemError(String message) {
        return new BusinessException(500, "系统错误: " + message);
    }
}
