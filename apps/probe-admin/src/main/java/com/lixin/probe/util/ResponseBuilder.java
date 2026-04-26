package com.lixin.probe.util;

import com.lixin.probe.common.PageResult;
import com.lixin.probe.common.Result;
import com.lixin.probe.constants.SystemConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 响应构建工具类
 * <p>
 * 简化Controller中的响应构建逻辑，减少重复代码。
 * 提供统一的响应格式和常见的构建模式。
 * </p>
 *
 * <p>主要功能:</p>
 * <ul>
 *   <li>构建成功响应（带数据）</li>
 *   <li>构建分页响应</li>
 *   <li>构建带统计信息的响应</li>
 *   <li>统一的错误处理</li>
 * </ul>
 *
 * @author Development Team
 * @date 2026-03-20
 * @version 1.0
 */
public class ResponseBuilder {

    /**
     * 构建成功响应（无数据）
     */
    public static Result<Void> success() {
        return Result.success();
    }

    /**
     * 构建成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return Result.success(data);
    }

    /**
     * 构建成功响应（带消息）
     */
    public static <T> Result<T> success(String message) {
        return Result.success(message);
    }

    /**
     * 构建成功响应（带数据和消息）
     */
    public static <T> Result<T> success(T data, String message) {
        return Result.success(message, data);
    }

    /**
     * 构建分页响应
     */
    public static <T> Result<PageResult<T>> page(List<T> list, long total, int pageNum, int pageSize) {
        PageResult<T> pageResult = new PageResult<>(list, total, pageNum, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 构建分页响应（使用总数自动计算）
     */
    public static <T> Result<PageResult<T>> page(List<T> list, long total) {
        return page(list, total, SystemConstants.Pagination.DEFAULT_PAGE_NUM,
                SystemConstants.Pagination.DEFAULT_PAGE_SIZE);
    }

    /**
     * 构建错误响应
     */
    public static Result<Void> error(String message) {
        return Result.error(message);
    }

    /**
     * 构建错误响应（带错误码）
     */
    public static Result<Void> error(int code, String message) {
        return Result.error(code, message);
    }

    /**
     * 构建带额外字段的响应
     * <p>
     * 当需要在标准响应格式之外添加额外字段时使用。
     * 例如：添加统计信息、关联数据等。
     * </p>
     *
     * @param data 主要数据
     * @param extras 额外字段键值对
     * @return 包含额外字段的Map响应
     */
    public static Map<String, Object> buildWithExtras(Object data, Object... extras) {
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);

        // 将键值对数组添加到Map
        for (int i = 0; i < extras.length; i += 2) {
            if (i + 1 < extras.length) {
                response.put(extras[i].toString(), extras[i + 1]);
            }
        }

        return response;
    }

    /**
     * 构建统计信息响应
     * <p>
     * 用于返回带统计数据的响应，常用于Dashboard、报表等场景。
     * </p>
     *
     * @param data 主要数据
     * @param statistics 统计信息Map
     * @return 包含统计信息的Map
     */
    public static Map<String, Object> buildWithStatistics(Object data, Map<String, Object> statistics) {
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        response.put("statistics", statistics);
        return response;
    }

    /**
     * 安全执行并返回响应
     * <p>
     * 包装可能抛出异常的操作，自动处理异常并返回错误响应。
     * </p>
     *
     * @param supplier 要执行的操作
     * @param errorMessage 异常时的错误消息
     * @param <T> 返回数据类型
     * @return 成功时返回数据，失败时返回错误响应
     */
    public static <T> Result<T> safeExecute(Supplier<T> supplier, String errorMessage) {
        try {
            T result = supplier.get();
            return Result.success(result);
        } catch (Exception e) {
            log.error("操作执行失败: {}", errorMessage, e);
            return Result.error(errorMessage);
        }
    }

    /**
     * 安全执行并返回响应（使用默认错误消息）
     */
    public static <T> Result<T> safeExecute(Supplier<T> supplier) {
        return safeExecute(supplier, "操作执行失败");
    }

    /**
     * 构建批量操作响应
     * <p>
     * 用于批量创建、更新、删除等操作的响应。
     * </p>
     *
     * @param totalCount 总数量
     * @param successCount 成功数量
     * @param failureCount 失败数量
     * @return 批量操作结果
     */
    public static Map<String, Object> buildBatchResult(int totalCount, int successCount, int failureCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("successCount", successCount);
        result.put("failureCount", failureCount);
        result.put("success", failureCount == 0);
        result.put("message", String.format("总数: %d, 成功: %d, 失败: %d",
                totalCount, successCount, failureCount));
        return result;
    }

    /**
     * 构建状态响应
     * <p>
     * 用于返回操作状态或系统状态的简单响应。
     * </p>
     *
     * @param status 状态值
     * @param message 状态描述
     * @return 状态响应
     */
    public static Map<String, Object> buildStatus(boolean status, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("message", message);
        return result;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResponseBuilder.class);
}