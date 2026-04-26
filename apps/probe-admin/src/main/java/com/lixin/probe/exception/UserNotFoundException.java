package com.lixin.probe.exception;

/**
 * 用户不存在异常
 *
 * @author Claude Code
 * @date 2026-03-11
 */
public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(Long userId) {
        super(ErrorCode.USER_NOT_FOUND.getCode(),
              "用户不存在: ID=" + userId);
    }

    public UserNotFoundException(String username) {
        super(ErrorCode.USER_NOT_FOUND.getCode(),
              "用户不存在: " + username);
    }
}
