package com.lixin.probe.exception;

/**
 * 插件未找到异常
 */
public class PluginNotFoundException extends RuntimeException {

    private final String code;

    public PluginNotFoundException(String message) {
        super(message);
        this.code = "PLUGIN_NOT_FOUND";
    }

    public PluginNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.code = "PLUGIN_NOT_FOUND";
    }

    public String getCode() {
        return code;
    }
}
