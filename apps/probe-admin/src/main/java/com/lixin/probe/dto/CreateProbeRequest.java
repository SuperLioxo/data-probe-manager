package com.lixin.probe.dto;

import com.lixin.probe.constants.SystemConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建探针请求DTO
 * <p>
 * 使用Bean Validation注解进行数据验证，
 * 替代手动参数检查，减少代码重复。
 * </p>
 *
 * @author Development Team
 * @date 2026-03-20
 */
@Data
@Schema(description = "创建探针请求")
public class CreateProbeRequest {

    /**
     * 探针标识（唯一）
     */
    @Schema(description = "探针标识", example = "system-probe-001", required = true)
    @NotBlank(message = "探针标识不能为空")
    @Size(min = SystemConstants.Probe.PROBE_KEY_MIN_LENGTH,
          max = SystemConstants.Probe.PROBE_KEY_MAX_LENGTH,
          message = "探针标识长度必须在{min}-{max}之间")
    @Pattern(regexp = SystemConstants.Regex.PROBE_KEY,
             message = "探针标识只能包含字母、数字、下划线和连字符")
    private String probeKey;

    /**
     * 探针名称
     */
    @Schema(description = "探针名称", example = "系统监控探针", required = true)
    @NotBlank(message = "探针名称不能为空")
    @Size(min = SystemConstants.Probe.PROBE_NAME_MIN_LENGTH,
          max = SystemConstants.Probe.PROBE_NAME_MAX_LENGTH,
          message = "探针名称长度必须在{min}-{max}之间")
    private String probeName;

    /**
     * 探针类型
     */
    @Schema(description = "探针类型", example = "SYSTEM", required = true)
    @NotBlank(message = "探针类型不能为空")
    @Pattern(regexp = SystemConstants.Regex.PROBE_TYPE,
             message = "探针类型必须是SYSTEM、FILE、DATABASE或UNIFIED")
    private String type;

    /**
     * 探针版本
     */
    @Schema(description = "探针版本", example = "1.0.0")
    @Size(max = SystemConstants.Probe.VERSION_MAX_LENGTH,
          message = "探针版本长度不能超过{max}")
    private String version;

    /**
     * 主机地址
     */
    @Schema(description = "主机地址", example = "192.168.1.100")
    @Pattern(regexp = SystemConstants.Regex.IP_ADDRESS,
             message = "IP地址格式不正确")
    private String host;

    /**
     * 端口号
     */
    @Schema(description = "端口号", example = "8080")
    @Min(value = SystemConstants.Probe.PORT_MIN, message = "端口最小值为{value}")
    @jakarta.validation.constraints.Max(value = SystemConstants.Probe.PORT_MAX, message = "端口最大值为{value}")
    private Integer port;

    /**
     * 采集间隔（秒）
     */
    @Schema(description = "采集间隔（秒）", example = "60")
    @Min(value = SystemConstants.Probe.COLLECT_INTERVAL_MIN, message = "采集间隔最小值为{value}")
    @jakarta.validation.constraints.Max(value = SystemConstants.Probe.COLLECT_INTERVAL_MAX, message = "采集间隔最大值为{value}")
    private Integer collectInterval;

    /**
     * 探针状态
     */
    @Schema(description = "探针状态", example = "ONLINE")
    @Pattern(regexp = SystemConstants.Regex.PROBE_STATUS,
             message = "探针状态必须是ONLINE、OFFLINE或DISABLED")
    private String status;

    /**
     * 配置信息（JSON格式）
     */
    @Schema(description = "配置信息（JSON格式）", example = "{\"timeout\":30}")
    @Size(max = SystemConstants.Probe.CONFIG_MAX_LENGTH,
          message = "配置信息长度不能超过{max}")
    private String config;

    /**
     * 描述
     */
    @Schema(description = "探针描述", example = "用于监控系统资源使用情况")
    @Size(max = 500, message = "描述长度不能超过500")
    private String description;
}