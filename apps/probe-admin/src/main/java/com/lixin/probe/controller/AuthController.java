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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 认证控制器 —— 负责系统中所有与身份认证相关的 HTTP 接口。
 *
 * <p>本控制器基于 JWT（JSON Web Token）机制实现无状态认证，提供了以下核心功能：</p>
 * <ul>
 *   <li>用户登录：验证用户名密码，签发 Access Token 和 Refresh Token</li>
 *   <li>获取当前用户信息：解析 Token 获取用户详情</li>
 *   <li>刷新令牌：使用 Refresh Token 换取新的 Token 对</li>
 *   <li>退出登录：将当前 Token 加入黑名单使其失效</li>
 * </ul>
 *
 * <p><b>认证流程说明：</b></p>
 * <ol>
 *   <li>客户端调用 /login 接口，传入用户名和密码</li>
 *   <li>服务端验证通过后，签发短期 Access Token（用于接口鉴权）和长期 Refresh Token（用于续签）</li>
 *   <li>客户端在后续请求的 Authorization 头中携带 Bearer Token 进行身份认证</li>
 *   <li>Access Token 过期后，客户端使用 Refresh Token 调用 /refresh 接口获取新的 Token 对</li>
 * </ol>
 *
 * <p><b>RBAC 权限模型：</b>
 * sys_user → sys_user_role → sys_role → sys_role_permission → sys_permission</p>
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (重构版)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 用户服务，负责用户查询、密码校验、角色与权限加载 */
    private final UserService userService;

    /** JWT 工具类，负责 Token 的生成、解析与验证 */
    private final JwtUtil jwtUtil;

    /**
     * Token 黑名单服务（可选依赖）。
     * 用于在用户退出登录时将 Token 加入黑名单使其立即失效。
     * 当该 Bean 未注册时不影响退出登录流程，因为 JWT 是无状态的，
     * 客户端删除 Token 即可完成退出。
     */
    @Autowired(required = false)
    private TokenBlacklistService tokenBlacklistService;

    /**
     * 用户登录接口。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>通过 UserService 验证用户名和密码</li>
     *   <li>检查用户账户状态（是否被禁用）</li>
     *   <li>生成 Access Token（短期令牌，用于接口鉴权）和 Refresh Token（长期令牌，用于续签）</li>
     *   <li>查询用户的角色列表和权限集合</li>
     *   <li>将 Token 信息和用户信息一并返回给客户端</li>
     * </ol>
     *
     * @param request 登录请求体，包含 username（用户名）和 password（密码），通过 @Valid 进行参数校验
     * @return 登录结果，包含：
     *   <ul>
     *     <li>accessToken - 短期访问令牌</li>
     *     <li>refreshToken - 长期刷新令牌</li>
     *     <li>tokenType - 令牌类型，固定为 "Bearer"</li>
     *     <li>expiresIn - Access Token 的过期时间（秒）</li>
     *     <li>userInfo - 用户基本信息（id、username、realName、email、phone、roles、permissions）</li>
     *   </ul>
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        // 第一步：验证用户名和密码，返回 User 对象；验证失败返回 null
        User user = userService.validateLogin(request.getUsername(), request.getPassword());

        if (user == null) {
            return Result.error(401, "用户名或密码错误");
        }

        // 第二步：检查账户状态，status == 0 表示账户已被管理员禁用
        if (user.getStatus() == 0) {
            return Result.error(403, "账户已被禁用");
        }

        // 第三步：构建 JWT Claims（自定义载荷），将用户名和真实姓名写入 Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", user.getUsername());
        claims.put("realName", user.getRealName());

        // 生成 Access Token —— 短期令牌，有效期较短（如 30 分钟），用于接口鉴权
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), claims);

        // 生成 Refresh Token —— 长期令牌，有效期较长（如 7 天），仅用于刷新 Access Token
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 组装返回给客户端的 Token 相关信息
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("tokenType", "Bearer");
        data.put("expiresIn", jwtUtil.getExpirationTime() / 1000);  // 毫秒转秒，便于前端计算过期倒计时

        // 第四步：查询用户的权限码集合和角色编码列表（用于前端权限控制和菜单渲染）
        Set<String> permissions = userService.getUserPermissions(user.getUsername());
        List<String> roles = userService.getUserRoles(user.getId());

        // 组装用户信息对象，一并返回给前端以便缓存展示
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("email", user.getEmail());
        userInfo.put("phone", user.getPhone());
        userInfo.put("roles", roles);
        userInfo.put("permissions", new ArrayList<>(permissions));
        data.put("userInfo", userInfo);

        return Result.success("登录成功", data);
    }

    /**
     * 获取当前登录用户信息接口。
     *
     * <p>根据请求头中的 Authorization Token 解析出用户 ID，查询并返回用户基本信息。
     * 前端在页面初始化或路由切换时调用此接口获取当前用户详情。</p>
     *
     * @param authHeader HTTP 请求头中的 Authorization 字段，格式为 "Bearer {token}"
     * @return 用户信息，包含 id、username、realName、email、phone
     */
    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo(@RequestHeader("Authorization") String authHeader) {
        return ControllerHelper.safeGet(() -> {
            // 去除 "Bearer " 前缀，提取纯 Token 字符串
            String token = authHeader.replace("Bearer ", "");

            // 验证 Token 的签名和有效期，无效则抛出异常
            if (!jwtUtil.validateToken(token)) {
                throw new IllegalArgumentException("Token无效或已过期");
            }

            // 从 Token 的 subject（主题）字段中解析出用户 ID
            Long userId = jwtUtil.getUserIdFromToken(token);

            // 通过用户 ID 查询最新的用户信息（确保获取的是数据库中的最新状态）
            User user = userService.getById(userId);
            if (user == null) {
                throw new IllegalArgumentException("用户不存在");
            }

            // 组装用户信息返回（注意此处不包含角色和权限，如需完整信息应在登录时缓存）
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
     * 刷新令牌接口。
     *
     * <p>当 Access Token 过期后，客户端使用之前获取的 Refresh Token 调用此接口，
     * 服务端会验证 Refresh Token 的有效性并签发新的 Access Token 和 Refresh Token 对。</p>
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>验证 Refresh Token 非空</li>
     *   <li>验证 Refresh Token 的签名和有效期</li>
     *   <li>验证 Token 类型是否为 Refresh Token（防止 Access Token 被滥用）</li>
     *   <li>从 Token 中解析用户信息并查询数据库确认用户状态</li>
     *   <li>签发新的 Token 对并返回</li>
     * </ol>
     *
     * @param request 请求体，需包含 "refreshToken" 字段
     * @return 新的 Token 信息，包含 accessToken、refreshToken、tokenType、expiresIn
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refreshToken(@RequestBody Map<String, String> request) {
        return ControllerHelper.safeGet(() -> {
            String refreshToken = request.get("refreshToken");

            // 前置校验：Refresh Token 不能为空
            if (refreshToken == null || refreshToken.isEmpty()) {
                throw new IllegalArgumentException("Refresh Token不能为空");
            }

            // 验证 Refresh Token 的签名是否合法、是否在有效期内
            if (!jwtUtil.validateToken(refreshToken)) {
                throw new IllegalArgumentException("Refresh Token无效或已过期");
            }

            // 类型校验：确保传入的是 Refresh Token 而非 Access Token
            // 这是为了防止攻击者用 Access Token 反复刷新
            if (!jwtUtil.isRefreshToken(refreshToken)) {
                throw new IllegalArgumentException("Token类型错误");
            }

            // 从 Refresh Token 中解析用户 ID 和用户名（无需再次查库验证密码）
            Long userId = jwtUtil.getUserIdFromToken(refreshToken);
            String username = jwtUtil.getUsernameFromToken(refreshToken);

            // 查询用户确认其存在且未被禁用（防止已删除或禁用的用户继续使用 Token）
            User user = userService.getById(userId);
            if (user == null) {
                throw new IllegalArgumentException("用户不存在");
            }

            if (user.getStatus() == 0) {
                throw new IllegalArgumentException("账户已被禁用");
            }

            // 构建新的 Claims 并签发新的 Token 对（双 Token 同时刷新，即 Rotation 策略）
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
     * 用户退出登录接口。
     *
     * <p>将当前 Token 加入黑名单，使其在剩余有效期内无法继续使用。
     * 由于 JWT 是无状态令牌（服务端不保存会话），因此退出登录采用了以下策略：</p>
     * <ul>
     *   <li>如果 TokenBlacklistService 可用，将 Token 加入黑名单进行服务端失效处理</li>
     *   <li>无论黑名单操作是否成功，均返回成功响应，因为客户端删除 Token 即可完成退出</li>
     * </ul>
     *
     * @param authHeader HTTP 请求头中的 Authorization 字段（可选），格式为 "Bearer {token}"
     * @return 退出登录结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // 如果客户端提供了 Authorization 头，尝试将 Token 加入黑名单
            if (authHeader != null && !authHeader.isEmpty()) {
                String token = authHeader.replace("Bearer ", "");

                if (tokenBlacklistService != null) {
                    // 将 Token 添加到黑名单，使其在剩余有效期内无法通过鉴权
                    tokenBlacklistService.addToBlacklist(token);
                }
            }
            // 即使黑名单操作失败或服务不可用，也返回成功。
            // 原因：JWT 是无状态的，客户端主动删除 Token 即可完成退出，
            // 黑名单只是额外的安全措施，用于防止 Token 被盗用后的滥用。
            return Result.success("退出登录成功");
        } catch (Exception e) {
            return Result.success("退出登录成功");
        }
    }
}
