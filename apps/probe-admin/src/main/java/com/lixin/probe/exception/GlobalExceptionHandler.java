package com.lixin.probe.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理应用中的各种异常，避免敏感信息泄露
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 处理探针不存在异常
     */
    @ExceptionHandler(ProbeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProbeNotFoundException(ProbeNotFoundException ex,
                                                                     HttpServletRequest request) {
        log.warn("探针不存在: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse(ex.getCode(), ex.getMessage(), request));
    }

    /**
     * 处理权限不足异常
     */
    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePermissionDeniedException(PermissionDeniedException ex,
                                                                         HttpServletRequest request) {
        log.warn("权限不足: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(createErrorResponse(ex.getCode(), ex.getMessage(), request));
    }

    /**
     * 处理用户不存在异常
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(UserNotFoundException ex,
                                                                     HttpServletRequest request) {
        log.warn("用户不存在: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse(ex.getCode(), ex.getMessage(), request));
    }

    /**
     * 处理插件不存在异常
     */
    @ExceptionHandler(PluginNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePluginNotFoundException(PluginNotFoundException ex,
                                                                       HttpServletRequest request) {
        log.warn("插件不存在: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse(ex.getCode(), ex.getMessage(), request));
    }

    /**
     * 处理Agent离线异常
     */
    @ExceptionHandler(AgentOfflineException.class)
    public ResponseEntity<ErrorResponse> handleAgentOfflineException(AgentOfflineException ex,
                                                                       HttpServletRequest request) {
        log.warn("Agent离线: agentCode={}", ex.getAgentCode());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createErrorResponse(ex.getCode(), ex.getMessage(), request));
    }

    /**
     * 处理插件命令执行异常
     */
    @ExceptionHandler(PluginCommandException.class)
    public ResponseEntity<ErrorResponse> handlePluginCommandException(PluginCommandException ex,
                                                                       HttpServletRequest request) {
        log.error("插件命令执行失败: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(ex.getCode(), ex.getMessage(), request));
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex,
                                                                    HttpServletRequest request) {
        log.warn("参数校验失败", ex);

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest()
                .body(createErrorResponse(ErrorCode.INVALID_PARAMETER.getCode(),
                                         "参数校验失败: " + message, request));
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException ex,
                                                             HttpServletRequest request) {
        log.warn("数据绑定失败", ex);

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest()
                .body(createErrorResponse(ErrorCode.INVALID_PARAMETER.getCode(),
                                         "参数格式错误: " + message, request));
    }

    /**
     * 处理约束违规异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex,
                                                                            HttpServletRequest request) {
        log.warn("约束违规", ex);

        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest()
                .body(createErrorResponse(ErrorCode.INVALID_PARAMETER.getCode(),
                                         "参数验证失败: " + message, request));
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex,
                                                                 HttpServletRequest request) {
        log.warn("业务异常: {}", ex.getMessage());

        return ResponseEntity.status(ex.getCode())
                .body(createErrorResponse(ex.getCode(), ex.getMessage(), request));
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                       HttpServletRequest request) {
        log.warn("非法参数: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(createErrorResponse(ErrorCode.INVALID_PARAMETER.getCode(),
                                         "参数错误: " + ex.getMessage(), request));
    }

    /**
     * 处理非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex,
                                                                     HttpServletRequest request) {
        log.error("非法状态: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(ErrorCode.SYSTEM_ERROR.getCode(),
                                         "服务状态异常，请稍后重试", request));
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex,
                                                               HttpServletRequest request) {
        // 记录完整的异常堆栈，便于调试
        log.error("运行时异常", ex);

        // 只返回友好的错误消息，不泄露敏感信息
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(ErrorCode.SYSTEM_ERROR.getCode(),
                                         "系统内部错误，请联系管理员", request));
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex,
                                                         HttpServletRequest request) {
        // 记录完整的异常堆栈
        log.error("未处理的异常", ex);

        // 返回友好的错误消息
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(ErrorCode.SYSTEM_ERROR.getCode(),
                                         "系统错误，请稍后重试", request));
    }

    /**
     * 处理安全异常（路径遍历、SQL注入等）
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex,
                                                                 HttpServletRequest request) {
        // 记录安全异常，警告级别
        log.warn("安全异常检测 - URI: {}, Message: {}, IP: {}",
                request.getRequestURI(),
                ex.getMessage(),
                getClientIp(request));

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(createErrorResponse(ErrorCode.SYSTEM_ERROR.getCode(),
                                         "请求被拒绝：不安全的操作", request));
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
     * 创建错误响应
     */
    private ErrorResponse createErrorResponse(String code, String message, HttpServletRequest request) {
        return new ErrorResponse(
                code,
                message,
                LocalDateTime.now().format(FORMATTER),
                request.getRequestURI()
        );
    }

    /**
     * 创建错误响应（int code重载）
     */
    private ErrorResponse createErrorResponse(int code, String message, HttpServletRequest request) {
        return new ErrorResponse(
                String.valueOf(code),
                message,
                LocalDateTime.now().format(FORMATTER),
                request.getRequestURI()
        );
    }

    /**
     * 错误响应DTO
     */
    public record ErrorResponse(
            String code,
            String message,
            String timestamp,
            String path
    ) {}
}
