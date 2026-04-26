package com.lixin.probe.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixin.probe.annotation.RequirePermission;
import com.lixin.probe.common.Result;
import com.lixin.probe.util.JwtUtil;
import com.lixin.probe.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 权限验证拦截器
 * 拦截带有@RequirePermission注解的请求，验证用户权限
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PermissionInterceptor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    private JwtUtil jwtUtil;

    @Autowired(required = false)
    private UserService userService;

    /**
     * 在请求处理之前进行权限检查
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只处理方法处理器
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();

        // 检查方法是否有@RequirePermission注解
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            // 检查类级别的注解
            requirePermission = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }

        if (requirePermission != null) {
            // 需要权限验证
            String requiredPermission = requirePermission.value();

            // 从请求头获取token
            String token = request.getHeader("Authorization");
            if (token == null || !token.startsWith("Bearer ")) {
                log.warn("未登录或token无效: {}", request.getRequestURI());
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或token无效");
                return false;
            }

            try {
                // 提取JWT token（去掉"Bearer "前缀）
                String jwtToken = token.substring(7);

                // 验证token并获取用户信息
                if (!jwtUtil.validateToken(jwtToken)) {
                    log.warn("token验证失败: {}", request.getRequestURI());
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "token验证失败");
                    return false;
                }

                // 获取用户名
                String username = jwtUtil.getUsernameFromToken(jwtToken);
                if (username == null) {
                    log.warn("无法从token中获取用户信息: {}", request.getRequestURI());
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "token无效");
                    return false;
                }

                // 从数据库查询用户权限列表
                Set<String> userPermissions = getUserPermissions(username);

                // 检查用户是否拥有所需权限
                if (!hasPermission(userPermissions, requiredPermission)) {
                    log.warn("权限不足: user={}, permission={}, uri={}",
                            username, requiredPermission, request.getRequestURI());
                    sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "权限不足");
                    return false;
                }

                log.debug("权限验证通过: user={}, permission={}, uri={}",
                        username, requiredPermission, request.getRequestURI());

            } catch (Exception e) {
                log.error("权限验证异常: {}", request.getRequestURI(), e);
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "token验证失败");
                return false;
            }
        }

        return true;
    }

    /**
     * 获取用户的权限列表
     * 从数据库查询用户的权限
     */
    private Set<String> getUserPermissions(String username) {
        if (userService == null) {
            log.warn("UserService 未注入，使用默认权限：仅admin用户有权限");
            // 后备方案：如果是admin用户，拥有所有权限
            if ("admin".equals(username)) {
                return Set.of("*");
            }
            return new HashSet<>();
        }

        try {
            // 从数据库查询用户权限
            Set<String> permissions = userService.getUserPermissions(username);
            log.debug("用户 {} 的权限: {}", username, permissions);
            return permissions;
        } catch (Exception e) {
            log.error("获取用户权限失败: {}", username, e);
            // 出错时返回空权限，拒绝访问
            return new HashSet<>();
        }
    }

    /**
     * 检查用户是否拥有指定权限
     */
    private boolean hasPermission(Set<String> userPermissions, String requiredPermission) {
        // 拥有通配符权限表示拥有所有权限
        if (userPermissions.contains("*")) {
            return true;
        }

        // 检查是否拥有所需权限
        return userPermissions.contains(requiredPermission);
    }

    /**
     * 发送错误响应
     * 使用Result类和ObjectMapper进行JSON序列化，防止XSS攻击
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        // 使用Result类和ObjectMapper进行安全的JSON序列化
        Result<Void> result = Result.error(status, message);
        String jsonResponse = objectMapper.writeValueAsString(result);

        response.getWriter().write(jsonResponse);
    }
}
