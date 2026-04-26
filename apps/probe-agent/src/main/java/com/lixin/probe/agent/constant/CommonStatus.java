package com.lixin.probe.agent.constant;

import java.util.Objects;

/**
 * 通用状态枚举
 * <p>
 * 状态码范围
 * 0 ~ 99 ：通用
 * 100 ~ 199 ：通信
 * 200 ~ 299 ：探查
 */
public enum CommonStatus {

    // ========== 200-299: 探查相关 ==========
    SUCCESS_PROBE(200, "探查成功"),
    SUCCESS_PROBE_EXECUTION(201, "探查指令执行成功"),
    SUCCESS_OUTPUT(202, "探查结果输出成功"),
    SUCCESS_DATABASE(210, "数据库探查成功"),
    SUCCESS_DATABASE_CONNECTION(211, "数据库连接成功"),
    SUCCESS_FILE(220, "文件探查成功"),
    SUCCESS_DIRECTORY_EXIST(221, "文件目录存在"),

    // ========== 100-199: 通信相关 ==========
    SUCCESS_COMM(100, "通信成功"),
    SUCCESS_SESSION(101, "会话正常"),
    SUCCESS_CONNECTION(102, "会话连接成功"),
    SUCCESS_DISCONNECTION(103, "会话断开成功"),
    SUCCESS_REGISTRATION(104, "会话接入成功"),
    SUCCESS_AUTHENTICATION(105, "会话认证成功"),
    SUCCESS_PARSING(111, "消息解析成功"),
    SUCCESS_EXECUTION(112, "消息指令执行成功"),
    SUCCESS_BUILD(113, "消息构建成功"),
    SUCCESS_HANDLE(114, "消息处理成功"),
    SUCCESS_ENCRYPT(115, "消息加密成功"),
    SUCCESS_DECRYPT(116, "消息解密成功"),
    SUCCESS_SEND(117, "消息发送成功"),

    // ========== 0-99: 通用 ==========
    SUCCESS(1, "成功"),
    UNKNOWN(0, "未知"),
    FAIL(-1, "失败"),

    // ========== 负数: 失败 ==========
    FAIL_COMM(-100, "通信失败"),
    FAIL_SESSION(-101, "会话异常"),
    FAIL_CONNECTION(-102, "会话连接失败"),
    FAIL_DISCONNECTION(-103, "会话断开失败"),
    FAIL_REGISTRATION(-104, "会话接入失败"),
    FAIL_AUTHENTICATION(-105, "会话认证失败"),
    FAIL_PARSING(-111, "消息解析失败"),
    FAIL_EXECUTION(-112, "消息指令执行失败"),
    FAIL_BUILD(-113, "消息构建失败"),
    FAIL_HANDLE(-114, "消息处理失败"),
    FAIL_ENCRYPT(-115, "消息加密失败"),
    FAIL_DECRYPT(-116, "消息解密失败"),
    FAIL_SEND(-117, "消息发送失败"),

    FAIL_PROBE(-200, "探查失败"),
    FAIL_PROBE_EXECUTION(-201, "探查指令执行失败"),
    FAIL_OUTPUT(-202, "探查结果输出失败"),
    FAIL_DATABASE(-210, "数据库探查失败"),
    FAIL_DATABASE_CONNECTION(-211, "数据库连接失败"),
    FAIL_DATABASE_UNSUPPORTED(-212, "数据库类型或版本不支持"),
    FAIL_FILE(-220, "文件探查失败"),
    FAIL_DIRECTORY_NOT_EXIST(-221, "文件目录不存在");

    private final Integer code;
    private final String message;

    // Manual constructor
    CommonStatus(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    // Getters
    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 判断 code 是否等于当前状态码
     *
     * @param code 状态码
     * @return 结果
     */
    public boolean equals(Integer code) {
        return Objects.equals(this.code, code);
    }
}
