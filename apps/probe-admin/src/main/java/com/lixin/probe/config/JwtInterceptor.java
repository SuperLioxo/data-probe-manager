package com.lixin.probe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixin.probe.annotation.RequirePermission;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.Permission;
import com.lixin.probe.service.PermissionService;
import com.lixin.probe.service.TokenBlacklistService;
import com.lixin.probe.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT拦截器 - 验证请求中的Token和权限
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtInterceptor.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PermissionService permissionService;

    @Autowired(required = false)
    private TokenBlacklistService tokenBlacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 处理跨域预检请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 获取Authorization header
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("请求缺少有效的Authorization header: {}", request.getRequestURI());
            sendErrorResponse(response, 401, "未登录或登录已过期");
            return false;
        }

        // 提取token
        String token = authHeader.substring(7);

        // 检查Token是否在黑名单中
        if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(token)) {
            log.warn("Token已在黑名单中（已退出登录）: {}", request.getRequestURI());
            sendErrorResponse(response, 401, "Token已失效，请重新登录");
            return false;
        }

        // 验证token
        if (!jwtUtil.validateToken(token)) {
            log.warn("Token验证失败: {}", request.getRequestURI());
            sendErrorResponse(response, 401, "Token无效或已过期");
            return false;
        }

        // Token有效，获取用户信息
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);

        // 将用户信息存入request attribute，供后续使用
        request.setAttribute("userId", userId);
        request.setAttribute("username", username);

        log.debug("用户认证成功: userId={}, username={}, uri={}", userId, username, request.getRequestURI());

        // 权限验证
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method method = handlerMethod.getMethod();

            // 检查方法是否有@RequirePermission注解
            RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
            if (requirePermission != null) {
                String requiredPermission = requirePermission.value();
                log.info("🔒 权限验证: userId={}, uri={}, requiredPermission={}", userId, request.getRequestURI(), requiredPermission);

                // 获取用户的所有权限
                if (permissionService == null) {
                    log.error("PermissionService 未注入，无法进行权限验证: userId={}", userId);
                    sendErrorResponse(response, 500, "系统配置错误");
                    return false;
                }

                List<Permission> permissions = permissionService.getPermissionsByUserId(userId);
                log.info("🔑 用户权限列表: userId={}, permissions={}", userId,
                    permissions.stream().map(p -> p.getPermissionCode()).collect(Collectors.toList()));

                // 空值安全检查：防止 permissions 为 null 或包含 null 元素
                if (permissions == null || permissions.isEmpty()) {
                    log.warn("用户权限为空: userId={}, uri={}", userId, request.getRequestURI());
                    sendErrorResponse(response, 403, "权限不足");
                    return false;
                }

                boolean hasPermission = permissions.stream()
                        .filter(p -> p != null && p.getPermissionCode() != null)
                        .anyMatch(p -> p.getPermissionCode().equals(requiredPermission));

                if (!hasPermission) {
                    log.warn("权限验证失败: userId={}, requiredPermission={}, userPermissions={}",
                            userId, requiredPermission,
                            permissions.stream().map(p -> p.getPermissionCode()).collect(Collectors.toList()));
                    sendErrorResponse(response, 403, "权限不足");
                    return false;
                }

                log.debug("用户权限验证通过: userId={}, permission={}", userId, requiredPermission);
            }
        }

        return true;
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result<?> result = Result.error(message);
        result.setCode(status);
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(result));
    }
}
