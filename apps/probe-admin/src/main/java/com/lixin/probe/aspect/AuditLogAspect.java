package com.lixin.probe.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixin.probe.annotation.Audited;
import com.lixin.probe.config.AuditLogProperties;
import com.lixin.probe.entity.AuditLog;
import com.lixin.probe.enums.AuditLogLevel;
import com.lixin.probe.enums.AuditLogOperation;
import com.lixin.probe.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审计日志切面
 * 自动记录带有@Audited注解的方法调用
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogProperties properties;

    @Around("@annotation(com.lixin.probe.annotation.Audited)")
    public Object recordAuditLog(ProceedingJoinPoint joinPoint) throws Throwable {
        // 检查是否启用审计日志
        if (!properties.isEnabled()) {
            log.warn("[AuditLog] 审计日志功能未启用");
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();

        // 获取注解
        Method method = getMethod(joinPoint);
        if (method == null) {
            log.warn("[AuditLog] 无法获取方法信息");
            return joinPoint.proceed();
        }

        Audited auditedAnnotation = method.getAnnotation(Audited.class);
        log.info("[AuditLog] 拦截到@Audited方法: {}", method.getName());

        // 获取请求信息
        HttpServletRequest request = getRequest();
        if (request == null) {
            log.warn("[AuditLog] 非HTTP请求（如定时任务），跳过审计日志记录");
            return joinPoint.proceed();
        }

        // 推断操作类型
        AuditLogOperation operationType = AuditLogOperation.fromCode(auditedAnnotation.operation());
        if (operationType == AuditLogOperation.OTHER) {
            operationType = AuditLogOperation.inferFromMethod(method.getName());
        }

        log.info("[AuditLog] 操作类型: {}, 是否记录查询操作: {}", operationType, properties.isLogQueryOperations());

        // 检查是否记录查询操作
        if (operationType == AuditLogOperation.QUERY && !properties.isLogQueryOperations()) {
            log.info("[AuditLog] 跳过记录查询操作");
            return joinPoint.proceed();
        }

        Object result = null;
        Integer responseCode = 200;
        String responseMessage = "success";
        Exception exception = null;

        try {
            // 执行方法
            result = joinPoint.proceed();

            // 尝试从Result对象中提取状态码
            responseCode = extractResponseCode(result);
            responseMessage = extractResponseMessage(result);

            // 检查是否记录成功操作
            if (responseCode == 200 && !properties.isLogSuccessOperations()) {
                return result;
            }

        } catch (Exception e) {
            exception = e;
            responseCode = 500;
            responseMessage = e.getMessage();

            // 检查是否记录失败操作
            if (!properties.isLogFailedOperations()) {
                throw e;
            }

            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            try {
                // 构建审计日志
                AuditLog auditLog = buildAuditLog(
                        joinPoint,
                        method,
                        auditedAnnotation,
                        operationType,
                        request,
                        responseCode,
                        responseMessage,
                        executionTime,
                        exception
                );

                log.info("[AuditLog] 准备保存审计日志: userId={}, operation={}, module={}, description={}",
                        auditLog.getUserId(), auditLog.getOperation(), auditLog.getModule(), auditLog.getDescription());

                // 根据配置选择同步或异步保存
                if (properties.isAsync()) {
                    log.info("[AuditLog] 异步保存审计日志");
                    auditLogService.createAsync(auditLog);
                } else {
                    log.info("[AuditLog] 同步保存审计日志");
                    auditLogService.create(auditLog);
                }

                log.info("[AuditLog] 审计日志保存请求已提交");

            } catch (Exception e) {
                log.error("记录审计日志失败: {}", e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * 构建审计日志对象
     */
    private AuditLog buildAuditLog(ProceedingJoinPoint joinPoint, Method method,
                                   Audited auditedAnnotation, AuditLogOperation operationType,
                                   HttpServletRequest request, Integer responseCode,
                                   String responseMessage, long executionTime, Exception exception) {

        // 确定日志级别
        AuditLogLevel level = determineLogLevel(operationType, responseCode, exception);

        // 提取请求参数
        String requestParams = extractRequestParams(request, joinPoint.getArgs(), method);

        // 构建描述
        String description = buildDescription(
                auditedAnnotation.description(),
                operationType,
                method.getName()
        );

        return AuditLog.builder()
                .userId(getCurrentUserId(request))
                .username(getCurrentUsername(request))
                .operation(operationType.getCode())
                .module(auditedAnnotation.module() != null && !auditedAnnotation.module().isEmpty()
                        ? auditedAnnotation.module() : extractModule(joinPoint))
                .description(description)
                .level(level.name())
                .method(joinPoint.getSignature().toString())
                .requestUrl(request.getRequestURI())
                .requestParams(requestParams)
                .responseCode(responseCode)
                .responseMessage(responseMessage)
                .executionTime(executionTime)
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .isException(exception != null)
                .exceptionMessage(exception != null ? exception.getMessage() : null)
                .build();
    }

    /**
     * 确定日志级别
     */
    private AuditLogLevel determineLogLevel(AuditLogOperation operationType,
                                            Integer responseCode, Exception exception) {
        // 如果有异常，根据操作类型确定级别
        if (exception != null) {
            if (operationType == AuditLogOperation.DELETE ||
                operationType == AuditLogOperation.PERMISSION_CHANGE ||
                operationType == AuditLogOperation.CONFIG_CHANGE) {
                return AuditLogLevel.CRITICAL;
            }
            return AuditLogLevel.ERROR;
        }

        // 根据操作类型确定级别
        switch (operationType) {
            case DELETE:
            case PERMISSION_CHANGE:
            case CONFIG_CHANGE:
                return AuditLogLevel.CRITICAL;
            case CREATE:
            case UPDATE:
            case BATCH:
                return responseCode == 200 ? AuditLogLevel.INFO : AuditLogLevel.ERROR;
            case LOGIN:
            case LOGOUT:
                return AuditLogLevel.INFO;
            case QUERY:
                return AuditLogLevel.INFO;
            default:
                return AuditLogLevel.INFO;
        }
    }

    /**
     * 构建描述
     */
    private String buildDescription(String annotationDescription, AuditLogOperation operation,
                                    String methodName) {
        if (annotationDescription != null && !annotationDescription.isEmpty()) {
            return annotationDescription;
        }

        return operation.getDescription() + " - " + methodName;
    }

    /**
     * 提取请求参数
     */
    private String extractRequestParams(HttpServletRequest request, Object[] args, Method method) {
        try {
            Map<String, Object> params = new HashMap<>();

            // 添加URL参数
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                params.put("query", queryString);
            }

            // 添加方法参数
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length && i < args.length; i++) {
                String paramName = parameters[i].getName();
                Object paramValue = args[i];

                // 过滤敏感参数
                if (isSensitiveParam(paramName)) {
                    params.put(paramName, "******");
                } else if (paramValue != null) {
                    // 限制参数长度
                    String paramStr = objectMapper.writeValueAsString(paramValue);
                    if (paramStr.length() > properties.getMaxParamLength()) {
                        paramStr = paramStr.substring(0, properties.getMaxParamLength()) + "...";
                    }
                    params.put(paramName, paramStr);
                }
            }

            if (params.isEmpty()) {
                return null;
            }

            String result = objectMapper.writeValueAsString(params);

            // 再次限制总长度
            if (result.length() > properties.getMaxParamLength()) {
                result = result.substring(0, properties.getMaxParamLength()) + "...";
            }

            return result;

        } catch (Exception e) {
            log.warn("提取请求参数失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否为敏感参数
     */
    private boolean isSensitiveParam(String paramName) {
        if (paramName == null) {
            return false;
        }

        String lowerName = paramName.toLowerCase();
        for (String sensitive : properties.getSensitiveParams()) {
            if (lowerName.contains(sensitive.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取响应消息
     */
    private String extractResponseMessage(Object result) {
        try {
            if (result instanceof com.lixin.probe.common.Result) {
                com.lixin.probe.common.Result<?> r = (com.lixin.probe.common.Result<?>) result;
                return r.getMessage() != null ? r.getMessage() : "success";
            }
            return "success";
        } catch (Exception e) {
            return "success";
        }
    }

    /**
     * 从Result对象中提取状态码
     */
    private Integer extractResponseCode(Object result) {
        try {
            if (result instanceof com.lixin.probe.common.Result) {
                com.lixin.probe.common.Result<?> r = (com.lixin.probe.common.Result<?>) result;
                return r.getCode() == 200 ? 200 : 500;
            }
            return 200;
        } catch (Exception e) {
            return 200;
        }
    }

    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId(HttpServletRequest request) {
        // 优先从request attribute中获取（由JWT拦截器设置）
        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return userId.toString();
        }

        // 其次从header中获取
        return request.getHeader("X-User-Id");
    }

    /**
     * 获取当前用户名
     */
    private String getCurrentUsername(HttpServletRequest request) {
        // 优先从request attribute中获取（由JWT拦截器设置）
        Object username = request.getAttribute("username");
        if (username != null) {
            return username.toString();
        }
        // 其次从header中获取
        return request.getHeader("X-Username");
    }

    /**
     * 获取请求对象
     */
    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 提取方法
     */
    private Method getMethod(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return signature.getMethod();
        } catch (Exception e) {
            log.error("获取方法失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取模块名称
     */
    private String extractModule(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        if (className.contains("Controller")) {
            return className.replace("Controller", "");
        }
        return className;
    }
}
