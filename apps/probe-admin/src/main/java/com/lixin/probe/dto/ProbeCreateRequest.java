package com.lixin.probe.dto;

import com.lixin.probe.constants.SystemConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 探针创建请求DTO
 * <p>
 * 使用Bean Validation注解进行自动验证，所有验证规则使用SystemConstants中定义的常量。
 * 当Controller方法参数使用@Valid注解时，这些验证规则会自动生效。
 * </p>
 *
 * @author Development Team
 * @since 1.0
 * @version 2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建探针请求")
public class ProbeCreateRequest {

    /**
     * 探针唯一标识
     */
    @Schema(description = "探针标识", example = "probe-system-001", required = true)
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
    private String name;

    /**
     * 探针类型
     * <p>支持的类型：SYSTEM（系统监控）、FILE（文件监控）、DATABASE（数据库监控）、UNIFIED（统一探针）</p>
     */
    @Schema(description = "探针类型", example = "SYSTEM",
            allowableValues = {"SYSTEM", "FILE", "DATABASE", "UNIFIED"})
    @Pattern(regexp = SystemConstants.Regex.PROBE_TYPE,
             message = "探针类型必须是SYSTEM、FILE、DATABASE或UNIFIED之一")
    @Builder.Default
    private String type = "SYSTEM";

    /**
     * 探针版本
     */
    @Schema(description = "探针版本", example = "1.0.0")
    @Size(max = SystemConstants.Probe.VERSION_MAX_LENGTH,
          message = "探针版本长度不能超过{max}")
    private String version;

    /**
     * 探针描述
     */
    @Schema(description = "探针描述", example = "监控系统资源使用情况")
    @Size(max = 500, message = "探针描述长度不能超过500")
    private String description;

    /**
     * 主机IP
     */
    @Schema(description = "主机IP地址", example = "192.168.1.100")
    @Pattern(regexp = SystemConstants.Regex.IP_ADDRESS, message = "IP地址格式不正确")
    private String hostIp;

    /**
     * 主机端口
     */
    @Schema(description = "主机端口", example = "9999")
    private Integer port;

    /**
     * 采集间隔（秒）
     */
    @Schema(description = "采集间隔（秒）", example = "60")
    private Integer collectInterval;

    /**
     * 配置信息（JSON格式）
     */
    @Schema(description = "配置信息（JSON格式）", example = "{\"threshold\":80}")
    @Size(max = SystemConstants.Probe.CONFIG_MAX_LENGTH,
          message = "配置信息长度不能超过{max}")
    private String config;
}
