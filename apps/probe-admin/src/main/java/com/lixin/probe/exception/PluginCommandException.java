package com.lixin.probe.exception;

/**
 * 插件命令异常
 */
public class PluginCommandException extends RuntimeException {

    private final String code;

    public PluginCommandException(String message) {
        super(message);
        this.code = "PLUGIN_COMMAND_ERROR";
    }

    public PluginCommandException(String message, Throwable cause) {
        super(message, cause);
        this.code = "PLUGIN_COMMAND_ERROR";
    }

    public String getCode() {
        return code;
    }
}
