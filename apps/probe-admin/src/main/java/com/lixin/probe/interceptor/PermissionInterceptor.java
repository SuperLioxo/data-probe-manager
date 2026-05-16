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
 * 权限验证拦截器 —— 基于@RequirePermission注解的细粒度访问控制
 *
 * <p>本拦截器是系统权限体系的第二道防线（在JwtInterceptor之后执行），负责对带有
 * {@link RequirePermission} 注解的Controller方法进行细粒度的权限校验。
 * 只有拥有相应权限编码的用户才能访问被注解保护的API接口。</p>
 *
 * <h3>与JwtInterceptor的权限校验区别：</h3>
 * <table border="1">
 *   <tr><th></th><th>JwtInterceptor</th><th>PermissionInterceptor</th></tr>
 *   <tr><td>权限来源</td><td>PermissionService直接查询</td><td>UserService查询</td></tr>
 *   <tr><td>注解支持</td><td>方法级@RequirePermission</td><td>方法级 + 类级@RequirePermission</td></tr>
 *   <tr><td>后备策略</td><td>无（PermissionService未注入则拒绝）</td><td>admin用户默认拥有所有权限</td></tr>
 *   <tr><td>执行位置</td><td>拦截器链第二层</td><td>拦截器链第三层（在JWT之后）</td></tr>
 * </table>
 *
 * <h3>权限校验流程：</h3>
 * <pre>
 *   请求到达（JWT认证已通过）
 *     │
 *     ▼
 *   目标方法/类有@RequirePermission注解？ ──否──→ 放行
 *     │ 是
 *     ▼
 *   从请求头提取JWT Token
 *     │
 *     ▼
 *   验证Token有效性并提取用户名
 *     │
 *     ▼
 *   从数据库查询用户权限列表
 *     │
 *     ▼
 *   用户拥有通配符权限("*")？ ──是──→ 放行（超级管理员）
 *     │ 否
 *     ▼
 *   用户拥有所需权限？ ──是──→ 放行
 *     │ 否
 *     ▼
 *   返回403权限不足
 * </pre>
 *
 * @see RequirePermission 权限注解，标注在Controller方法或类上
 * @see com.lixin.probe.config.WebConfig WebConfig中注册本拦截器
 * @see com.lixin.probe.config.JwtInterceptor JWT认证拦截器
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PermissionInterceptor.class);

    /**
     * JSON序列化工具
     * 使用ObjectMapper进行JSON输出，自动转义特殊字符，防止XSS攻击
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** JWT工具类，用于解析和验证Token */
    @org.springframework.beans.factory.annotation.Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户服务（可选注入）
     * 用于从数据库查询用户权限列表。如果未注入，将使用后备策略：
     * 仅admin用户拥有所有权限（通配符"*"），其他用户无任何权限。
     */
    @Autowired(required = false)
    private UserService userService;

    /**
     * 请求预处理 —— 执行权限校验
     *
     * <p>在JWT认证通过后（由JwtInterceptor完成），本方法进一步检查目标Controller方法
     * 是否需要特定权限。权限检查支持方法级和类级两种粒度：</p>
     * <ul>
     *   <li><b>方法级</b>：标注在具体方法上，只对该方法生效</li>
     *   <li><b>类级</b>：标注在Controller类上，对类中所有方法生效（方法级注解优先）</li>
     * </ul>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  目标处理器（Controller方法）
     * @return true表示权限校验通过，false表示权限不足或校验失败
     * @throws Exception 处理异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只处理Spring MVC的方法处理器（Controller方法），跳过静态资源等
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();

        // 优先检查方法级别的@RequirePermission注解
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            // 方法级别没有注解，再检查类级别的@RequirePermission注解
            requirePermission = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }

        // 如果方法或类上有@RequirePermission注解，则进行权限验证
        if (requirePermission != null) {
            // 获取注解中声明的权限编码（如 "user:create"、"data:delete" 等）
            String requiredPermission = requirePermission.value();

            // 从请求头获取JWT Token（JWT认证已在前置拦截器中通过）
            String token = request.getHeader("Authorization");
            if (token == null || !token.startsWith("Bearer ")) {
                log.warn("未登录或token无效: {}", request.getRequestURI());
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或token无效");
                return false;
            }

            try {
                // 提取JWT token（去掉"Bearer "前缀，共7个字符）
                String jwtToken = token.substring(7);

                // 再次验证token有效性（双重保障）
                if (!jwtUtil.validateToken(jwtToken)) {
                    log.warn("token验证失败: {}", request.getRequestURI());
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "token验证失败");
                    return false;
                }

                // 从Token中提取用户名
                String username = jwtUtil.getUsernameFromToken(jwtToken);
                if (username == null) {
                    log.warn("无法从token中获取用户信息: {}", request.getRequestURI());
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "token无效");
                    return false;
                }

                // 从数据库查询该用户拥有的所有权限编码集合
                Set<String> userPermissions = getUserPermissions(username);

                // 检查用户是否拥有所需的权限编码
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

        // 无@RequirePermission注解，或权限校验通过，放行请求
        return true;
    }

    /**
     * 获取用户权限列表
     *
     * <p>从数据库查询指定用户拥有的所有权限编码集合。</p>
     *
     * <p>后备策略：</p>
     * <ul>
     *   <li>如果UserService未注入（某些测试或精简部署场景），使用默认策略：
     *       admin用户拥有通配符权限"*"（等同于超级管理员），其他用户无任何权限</li>
     *   <li>如果查询过程中出现异常，返回空权限集（拒绝所有访问），确保安全</li>
     * </ul>
     *
     * @param username 用户名
     * @return 用户拥有的权限编码集合
     */
    private Set<String> getUserPermissions(String username) {
        if (userService == null) {
            log.warn("UserService 未注入，使用默认权限：仅admin用户有权限");
            // 后备方案：如果是admin用户，拥有所有权限
            if ("admin".equals(username)) {
                return Set.of("*");  // 通配符权限，等同于超级管理员
            }
            return new HashSet<>();  // 非admin用户无权限
        }

        try {
            // 从数据库查询用户权限
            Set<String> permissions = userService.getUserPermissions(username);
            log.debug("用户 {} 的权限: {}", username, permissions);
            return permissions;
        } catch (Exception e) {
            log.error("获取用户权限失败: {}", username, e);
            // 出错时返回空权限，拒绝访问（fail-safe策略）
            return new HashSet<>();
        }
    }

    /**
     * 检查用户是否拥有指定权限
     *
     * <p>权限匹配规则：</p>
     * <ul>
     *   <li>通配符权限 "*" 匹配所有权限（超级管理员）</li>
     *   <li>精确匹配：用户权限列表中包含所需权限编码</li>
     * </ul>
     *
     * @param userPermissions    用户拥有的权限集合
     * @param requiredPermission 所需的权限编码
     * @return true表示拥有所需权限
     */
    private boolean hasPermission(Set<String> userPermissions, String requiredPermission) {
        // 拥有通配符权限表示拥有所有权限（超级管理员）
        if (userPermissions.contains("*")) {
            return true;
        }

        // 检查是否拥有所需的精确权限
        return userPermissions.contains(requiredPermission);
    }

    /**
     * 发送错误响应到客户端
     *
     * <p>使用 {@link Result} 类和 {@link ObjectMapper} 进行JSON序列化，
     * ObjectMapper会自动转义HTML特殊字符（如 &lt; &gt; &amp;），防止通过错误消息
     * 进行XSS攻击。</p>
     *
     * @param response HTTP响应对象
     * @param status   HTTP状态码
     * @param message  错误提示消息
     * @throws IOException 写入响应时的IO异常
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        // 使用Result类和ObjectMapper进行安全的JSON序列化（防止XSS）
        Result<Void> result = Result.error(status, message);
        String jsonResponse = objectMapper.writeValueAsString(result);

        response.getWriter().write(jsonResponse);
    }
}
