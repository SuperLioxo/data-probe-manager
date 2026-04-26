package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.dto.LoginRequest;
import com.lixin.probe.entity.User;
import com.lixin.probe.service.TokenBlacklistService;
import com.lixin.probe.service.UserService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器（重构版）
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (重构版)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Autowired(required = false)
    private TokenBlacklistService tokenBlacklistService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.validateLogin(request.getUsername(), request.getPassword());

        if (user == null) {
            return Result.error("用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            return Result.error("账户已被禁用");
        }

        // 生成JWT Claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", user.getUsername());
        claims.put("realName", user.getRealName());

        // 生成Access Token (短期)
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), claims);

        // 生成Refresh Token (长期)
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("tokenType", "Bearer");
        data.put("expiresIn", jwtUtil.getExpirationTime() / 1000);  // 转换为秒
        data.put("userInfo", Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "realName", user.getRealName(),
            "email", user.getEmail(),
            "phone", user.getPhone()
        ));

        return Result.success("登录成功", data);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo(@RequestHeader("Authorization") String authHeader) {
        return ControllerHelper.safeGet(() -> {
            // 移除 "Bearer " 前缀
            String token = authHeader.replace("Bearer ", "");

            // 验证token
            if (!jwtUtil.validateToken(token)) {
                throw new IllegalArgumentException("Token无效或已过期");
            }

            // 从token中获取用户ID
            Long userId = jwtUtil.getUserIdFromToken(token);

            // 通过用户ID查询用户
            User user = userService.getById(userId);
            if (user == null) {
                throw new IllegalArgumentException("用户不存在");
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("realName", user.getRealName());
            userInfo.put("email", user.getEmail());
            userInfo.put("phone", user.getPhone());

            return userInfo;
        }, "获取用户信息失败");
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refreshToken(@RequestBody Map<String, String> request) {
        return ControllerHelper.safeGet(() -> {
            String refreshToken = request.get("refreshToken");

            if (refreshToken == null || refreshToken.isEmpty()) {
                throw new IllegalArgumentException("Refresh Token不能为空");
            }

            // 验证Refresh Token
            if (!jwtUtil.validateToken(refreshToken)) {
                throw new IllegalArgumentException("Refresh Token无效或已过期");
            }

            // 验证是否为Refresh Token类型
            if (!jwtUtil.isRefreshToken(refreshToken)) {
                throw new IllegalArgumentException("Token类型错误");
            }

            // 从Refresh Token中获取用户信息
            Long userId = jwtUtil.getUserIdFromToken(refreshToken);
            String username = jwtUtil.getUsernameFromToken(refreshToken);

            // 查询用户
            User user = userService.getById(userId);
            if (user == null) {
                throw new IllegalArgumentException("用户不存在");
            }

            if (user.getStatus() == 0) {
                throw new IllegalArgumentException("账户已被禁用");
            }

            // 生成新的Access Token和Refresh Token
            Map<String, Object> claims = new HashMap<>();
            claims.put("username", user.getUsername());
            claims.put("realName", user.getRealName());

            String newAccessToken = jwtUtil.generateAccessToken(userId, username, claims);
            String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);

            Map<String, Object> data = new HashMap<>();
            data.put("accessToken", newAccessToken);
            data.put("refreshToken", newRefreshToken);
            data.put("tokenType", "Bearer");
            data.put("expiresIn", jwtUtil.getExpirationTime() / 1000);

            return data;
        }, "刷新Token失败");
    }

    /**
     * 用户退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // 如果提供了Authorization header，将Token加入黑名单
            if (authHeader != null && !authHeader.isEmpty()) {
                String token = authHeader.replace("Bearer ", "");

                if (tokenBlacklistService != null) {
                    // 将Token添加到黑名单，使其失效
                    tokenBlacklistService.addToBlacklist(token);
                }
            }
            // 即使黑名单操作失败，也返回成功，因为JWT是无状态的
            // 只要客户端删除Token，退出登录就完成了
            return Result.success("退出登录成功");
        } catch (Exception e) {
            return Result.success("退出登录成功");
        }
    }

    /**
     * 临时端点：生成密码哈希（仅用于开发环境）
     */
    @GetMapping("/generate-password")
    public Result<Map<String, String>> generatePassword(@RequestParam(defaultValue = "admin123") String password) {
        String hash = com.lixin.probe.util.SecurityUtil.encryptPassword(password);
        Map<String, String> result = new HashMap<>();
        result.put("password", password);
        result.put("hash", hash);
        return Result.success(result);
    }
}
