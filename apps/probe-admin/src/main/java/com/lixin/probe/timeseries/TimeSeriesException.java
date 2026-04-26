package com.lixin.probe.timeseries;

/**
 * 时间序列数据库异常
 *
 * <p>当时间序列数据库操作失败时抛出此异常。</p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public class TimeSeriesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造异常
     *
     * @param message 异常消息
     */
    public TimeSeriesException(String message) {
        super(message);
    }

    /**
     * 构造异常
     *
     * @param message 异常消息
     * @param cause 原因
     */
    public TimeSeriesException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造异常
     *
     * @param cause 原因
     */
    public TimeSeriesException(Throwable cause) {
        super(cause);
    }
}
