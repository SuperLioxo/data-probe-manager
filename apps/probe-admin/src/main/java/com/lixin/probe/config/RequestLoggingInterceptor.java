package com.lixin.probe.config;

import com.lixin.probe.util.SensitiveDataUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Enumeration;
import java.util.UUID;

/**
 * 请求日志拦截器
 * 记录所有API请求的详细信息
 *
 * @author Claude Code
 * @date 2026-04-12
 */
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

    private static final String START_TIME_ATTR = "requestStartTime";
    private static final String REQUEST_ID_ATTR = "requestId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 生成请求ID
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        request.setAttribute(REQUEST_ID_ATTR, requestId);

        // 设置MDC
        MDC.put("requestId", requestId);
        MDC.put("method", request.getMethod());
        MDC.put("uri", request.getRequestURI());
        MDC.put("clientIp", getClientIp(request));

        // 记录开始时间
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        // 记录请求信息
        if (log.isInfoEnabled()) {
            log.info("=> 请求开始 [{}] {} {}",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI());

            // 记录请求参数（脱敏）
            logRequestParams(request, requestId);
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                          Object handler, ModelAndView modelAndView) {
        // 后处理，可以在这里记录响应信息
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        // 计算处理时间
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        String requestId = (String) request.getAttribute(REQUEST_ID_ATTR);

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;

            // 记录请求完成
            log.info("<= 请求完成 [{}] {} {} - 状态码: {} - 耗时: {}ms",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);

            // 慢请求警告
            if (duration > 1000) {
                log.warn("[{}] 慢请求警告: 耗时 {}ms", requestId, duration);
            }
        }

        // 清理MDC
        MDC.remove("requestId");
        MDC.remove("method");
        MDC.remove("uri");
        MDC.remove("clientIp");
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 记录请求参数（脱敏）
     */
    private void logRequestParams(HttpServletRequest request, String requestId) {
        if (!log.isDebugEnabled()) {
            return;
        }

        // 记录查询参数
        Enumeration<String> paramNames = request.getParameterNames();
        if (paramNames != null && paramNames.hasMoreElements()) {
            StringBuilder params = new StringBuilder();
            while (paramNames.hasMoreElements()) {
                String name = paramNames.nextElement();
                String value = request.getParameter(name);

                // 脱敏处理
                String sanitizedValue = SensitiveDataUtils.sanitize(value);
                params.append(name).append("=").append(sanitizedValue);

                if (paramNames.hasMoreElements()) {
                    params.append("&");
                }
            }

            log.debug("[{}] 参数: {}", requestId, params);
        }

        // 记录请求头（部分）
        String contentType = request.getContentType();
        String userAgent = request.getHeader("User-Agent");
        String authorization = request.getHeader("Authorization");

        if (contentType != null) {
            log.debug("[{}] Content-Type: {}", requestId, contentType);
        }
        if (userAgent != null) {
            log.debug("[{}] User-Agent: {}", requestId, userAgent);
        }
        if (authorization != null) {
            // 脱敏Token
            String sanitizedAuth = SensitiveDataUtils.sanitize(authorization);
            log.debug("[{}] Authorization: {}", requestId, sanitizedAuth);
        }
    }
}
