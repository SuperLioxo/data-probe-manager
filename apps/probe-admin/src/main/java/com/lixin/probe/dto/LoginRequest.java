package com.lixin.probe.dto;

import com.lixin.probe.constants.SystemConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求DTO
 * <p>
 * 使用Bean Validation注解进行自动验证，所有验证规则使用SystemConstants中定义的常量。
 * </p>
 *
 * @author Development Team
 * @version 2.0
 */
@Data
@Schema(description = "用户登录请求")
public class LoginRequest {

    /**
     * 用户名
     */
    @Schema(description = "用户名", example = "admin", required = true)
    @NotBlank(message = "用户名不能为空")
    @Size(min = SystemConstants.User.USERNAME_MIN_LENGTH,
          max = SystemConstants.User.USERNAME_MAX_LENGTH,
          message = "用户名长度必须在{min}-{max}之间")
    @Pattern(regexp = SystemConstants.Regex.USERNAME,
             message = "用户名只能包含字母、数字、下划线、@、点和连字符")
    private String username;

    /**
     * 密码
     */
    @Schema(description = "密码", example = "admin123", required = true)
    @NotBlank(message = "密码不能为空")
    @Size(min = SystemConstants.User.PASSWORD_MIN_LENGTH,
          max = SystemConstants.User.PASSWORD_MAX_LENGTH,
          message = "密码长度必须在{min}-{max}之间")
    private String password;
}
