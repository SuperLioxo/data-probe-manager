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
 * JWT认证拦截器 —— 系统安全防线的核心组件
 *
 * <p>本拦截器是整个数据探针管理平台的安全网关，负责在所有受保护的API请求到达Controller之前，
 * 对请求进行身份认证和权限校验。它是基于Spring MVC的HandlerInterceptor机制实现的，
 * 在WebConfig中注册并配置拦截路径。</p>
 *
 * <h3>认证流程（按执行顺序）：</h3>
 * <ol>
 *   <li><b>跨域预检放行</b> —— 对OPTIONS请求直接放行，确保前端跨域请求正常工作</li>
 *   <li><b>Token提取</b> —— 从请求头Authorization中提取Bearer Token</li>
 *   <li><b>黑名单检查</b> —— 检查Token是否已被加入黑名单（如用户主动退出登录）</li>
 *   <li><b>Token有效性验证</b> —— 通过JwtUtil验证签名、过期时间等</li>
 *   <li><b>用户信息注入</b> —— 将userId和username注入到request属性中，供后续业务逻辑使用</li>
 *   <li><b>权限校验</b> —— 如果目标方法带有@RequirePermission注解，则进行细粒度权限验证</li>
 * </ol>
 *
 * <h3>与其他组件的关系：</h3>
 * <ul>
 *   <li>{@link JwtUtil} —— JWT令牌的生成、解析和验证工具</li>
 *   <li>{@link TokenBlacklistService} —— Token黑名单管理，用于处理用户登出场景</li>
 *   <li>{@link PermissionService} —— 用户权限查询服务</li>
 *   <li>{@link WebConfig} —— 在该配置类中注册本拦截器并配置拦截/排除路径</li>
 *   <li>{@link RateLimitInterceptor} —— 在本拦截器之前执行的登录限流拦截器</li>
 * </ul>
 *
 * @see WebConfig#addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry)
 * @see JwtUtil
 * @see TokenBlacklistService
 * @see RequirePermission
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtInterceptor.class);

    /** JWT工具类，负责Token的解析、验证和用户信息提取 */
    @Autowired
    private JwtUtil jwtUtil;

    /** 权限服务，用于查询用户拥有的权限列表 */
    @Autowired
    private PermissionService permissionService;

    /**
     * Token黑名单服务（可选注入）
     * 当用户主动退出登录时，其Token会被加入黑名单，即使Token尚未过期也无法继续使用。
     * 使用required=false是因为在某些测试场景下可能不需要该服务。
     */
    @Autowired(required = false)
    private TokenBlacklistService tokenBlacklistService;

    /**
     * 请求预处理 —— 执行JWT认证和权限校验
     *
     * <p>该方法是拦截器的核心入口，在每个受保护的API请求到达Controller之前被调用。
     * 方法返回true表示认证通过，请求继续执行；返回false表示认证失败，请求被拦截。</p>
     *
     * <p>处理流程：</p>
     * <pre>
     *   请求进入
     *     │
     *     ▼
     *   OPTIONS请求？ ──是──→ 直接放行（CORS预检）
     *     │ 否
     *     ▼
     *   Authorization头存在？ ──否──→ 返回401未登录
     *     │ 是
     *     ▼
     *   Token在黑名单中？ ──是──→ 返回401 Token已失效
     *     │ 否
     *     ▼
     *   Token验证通过？ ──否──→ 返回401 Token无效
     *     │ 是
     *     ▼
     *   提取用户信息并注入request
     *     │
     *     ▼
     *   方法有@RequirePermission？ ──否──→ 放行
     *     │ 是
     *     ▼
     *   用户拥有所需权限？ ──否──→ 返回403权限不足
     *     │ 是
     *     ▼
     *   放行，请求到达Controller
     * </pre>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  目标处理器（Controller方法）
     * @return true表示通过认证，请求继续；false表示认证失败，请求被拦截
     * @throws Exception 可能的异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // ====== 第一步：处理跨域预检请求 ======
        // 浏览器在发送跨域请求前会先发送OPTIONS预检请求，这里直接放行，
        // 否则前端会因为预检请求被拦截而无法正常访问API
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // ====== 第二步：从请求头提取JWT Token ======
        // 前端在请求头中使用标准格式 "Authorization: Bearer <token>" 携带JWT令牌
        String authHeader = request.getHeader("Authorization");

        // 如果请求头不存在或格式不正确（不以"Bearer "开头），则拒绝请求
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("请求缺少有效的Authorization header: {}", request.getRequestURI());
            sendErrorResponse(response, 401, "未登录或登录已过期");
            return false;
        }

        // 去掉"Bearer "前缀（共7个字符），提取纯Token字符串
        String token = authHeader.substring(7);

        // ====== 第三步：检查Token黑名单 ======
        // 用户主动退出登录时，其Token会被加入黑名单。即使Token尚未过期，也无法继续使用。
        // 这解决了"用户退出后Token仍然有效"的安全隐患。
        if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(token)) {
            log.warn("Token已在黑名单中（已退出登录）: {}", request.getRequestURI());
            sendErrorResponse(response, 401, "Token已失效，请重新登录");
            return false;
        }

        // ====== 第四步：验证Token的有效性 ======
        // JwtUtil会验证Token的签名是否正确、是否过期、格式是否合法等
        if (!jwtUtil.validateToken(token)) {
            log.warn("Token验证失败: {}", request.getRequestURI());
            sendErrorResponse(response, 401, "Token无效或已过期");
            return false;
        }

        // ====== 第五步：提取用户信息并注入到请求属性中 ======
        // Token验证通过后，从Token的payload中提取用户ID和用户名，
        // 存入request属性中，后续Controller可以通过request.getAttribute()获取
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);

        // 将用户信息存入request attribute，供后续使用
        request.setAttribute("userId", userId);
        request.setAttribute("username", username);

        log.debug("用户认证成功: userId={}, username={}, uri={}", userId, username, request.getRequestURI());

        // ====== 第六步：权限校验（基于注解的细粒度权限控制） ======
        // 检查目标Controller方法是否标注了@RequirePermission注解
        // 如果有，则需要验证当前用户是否拥有该注解指定的权限
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method method = handlerMethod.getMethod();

            // 检查方法是否有@RequirePermission注解
            RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
            if (requirePermission != null) {
                // 获取注解中声明的所需权限编码，例如 "user:create"、"data:delete" 等
                String requiredPermission = requirePermission.value();
                log.info("🔒 权限验证: userId={}, uri={}, requiredPermission={}", userId, request.getRequestURI(), requiredPermission);

                // 获取用户的所有权限
                // 如果PermissionService未注入，说明系统配置有问题，直接拒绝请求
                if (permissionService == null) {
                    log.error("PermissionService 未注入，无法进行权限验证: userId={}", userId);
                    sendErrorResponse(response, 500, "系统配置错误");
                    return false;
                }

                // 从数据库查询该用户拥有的所有权限列表
                List<Permission> permissions = permissionService.getPermissionsByUserId(userId);
                log.info("🔑 用户权限列表: userId={}, permissions={}", userId,
                    permissions.stream().map(p -> p.getPermissionCode()).collect(Collectors.toList()));

                // 空值安全检查：防止 permissions 为 null 或包含 null 元素
                // 如果用户没有任何权限，直接拒绝
                if (permissions == null || permissions.isEmpty()) {
                    log.warn("用户权限为空: userId={}, uri={}", userId, request.getRequestURI());
                    sendErrorResponse(response, 403, "权限不足");
                    return false;
                }

                // 遍历用户权限列表，检查是否拥有所需的权限编码
                // 过滤掉null元素以防止NPE
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

        // 所有验证通过，请求继续执行
        return true;
    }

    /**
     * 发送错误响应到客户端
     *
     * <p>将错误信息封装为统一的JSON格式返回给前端，使用Result类保证响应格式的一致性。
     * 设置UTF-8编码和JSON Content-Type，确保中文错误消息正常显示。</p>
     *
     * @param response HTTP响应对象
     * @param status   HTTP状态码（如401未认证、403禁止访问、500服务器错误）
     * @param message  错误提示消息
     * @throws Exception 写入响应时的IO异常
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
