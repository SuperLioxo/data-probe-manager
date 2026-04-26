package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@TableName("sys_user")
public class User {

    /**
     * 用户ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空", groups = {Create.class, Update.class})
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间", groups = {Create.class, Update.class})
    @Pattern(regexp = "^[a-zA-Z0-9_@.-]+$", message = "用户名只能包含字母、数字、下划线、@、点和连字符", groups = {Create.class, Update.class})
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空", groups = {Create.class})
    @Size(min = 6, max = 100, message = "密码长度必须在6-100之间", groups = {Create.class, Update.class})
    private String password;

    /**
     * 真实姓名
     */
    @Size(max = 50, message = "真实姓名长度不能超过50", groups = {Create.class, Update.class})
    private String realName;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确", groups = {Create.class, Update.class})
    @Size(max = 100, message = "邮箱长度不能超过100", groups = {Create.class, Update.class})
    private String email;

    /**
     * 手机号
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确", groups = {Create.class, Update.class})
    private String phone;

    /**
     * 状态：0-禁用，1-正常
     */
    @Min(value = 0, message = "状态值必须为0或1", groups = {Create.class, Update.class})
    @Max(value = 1, message = "状态值必须为0或1", groups = {Create.class, Update.class})
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建分组
     */
    public interface Create {}

    /**
     * 更新分组
     */
    public interface Update {}
}
