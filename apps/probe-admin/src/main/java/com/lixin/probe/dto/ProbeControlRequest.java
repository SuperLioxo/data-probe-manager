package com.lixin.probe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Map;

/**
 * 探针控制请求DTO（带验证）
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (添加验证注解)
 */
@Data
public class ProbeControlRequest {
    /**
     * 探针标识
     */
    @NotBlank(message = "探针标识不能为空")
    @Size(min = 1, max = 100, message = "探针标识长度必须在1-100之间")
    private String probeKey;

    /**
     * 命令类型：START、STOP、RESTART、UPDATE_CONFIG
     */
    @NotBlank(message = "命令类型不能为空")
    @Pattern(regexp = "^(START|STOP|RESTART|UPDATE_CONFIG)$", message = "命令类型必须是START、STOP、RESTART或UPDATE_CONFIG之一")
    private String commandType;

    /**
     * 命令参数（可选）
     */
    private Map<String, Object> params;
}
