package com.lixin.probe.agent.result;

import com.lixin.probe.agent.constant.CommonStatus;

/**
 * 通用响应结果
 */
public class CommonResult<T> {

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 时间戳
     */
    private Long timestamp;

    // Constructor
    public CommonResult() {}

    public CommonResult(Integer code, String message, T data, Long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    // Builder
    public static <T> Builder<T> builder() { return new Builder<>(); }

    public static class Builder<T> {
        private Integer code;
        private String message;
        private T data;
        private Long timestamp;

        public Builder<T> code(Integer code) { this.code = code; return this; }
        public Builder<T> message(String message) { this.message = message; return this; }
        public Builder<T> data(T data) { this.data = data; return this; }
        public Builder<T> timestamp(Long timestamp) { this.timestamp = timestamp; return this; }

        public CommonResult<T> build() {
            return new CommonResult<>(code, message, data, timestamp);
        }
    }

    /**
     * 成功响应
     */
    public static <T> CommonResult<T> success() {
        return success(null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> CommonResult<T> success(T data) {
        return CommonResult.<T>builder()
                .code(CommonStatus.SUCCESS.getCode())
                .message(CommonStatus.SUCCESS.getMessage())
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> CommonResult<T> success(String message, T data) {
        return CommonResult.<T>builder()
                .code(CommonStatus.SUCCESS.getCode())
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败响应
     */
    public static <T> CommonResult<T> fail() {
        return fail(CommonStatus.FAIL.getMessage());
    }

    /**
     * 失败响应（自定义消息）
     */
    public static <T> CommonResult<T> fail(String message) {
        return CommonResult.<T>builder()
                .code(CommonStatus.FAIL.getCode())
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败响应（指定状态码）
     */
    public static <T> CommonResult<T> fail(CommonStatus status) {
        return CommonResult.<T>builder()
                .code(status.getCode())
                .message(status.getMessage())
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败响应（指定状态码和自定义消息）
     */
    public static <T> CommonResult<T> fail(CommonStatus status, String message) {
        return CommonResult.<T>builder()
                .code(status.getCode())
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return CommonStatus.SUCCESS.equals(this.code);
    }

    /**
     * 判断是否失败
     */
    public boolean isFail() {
        return !isSuccess();
    }
}
