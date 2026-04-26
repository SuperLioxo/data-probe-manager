package com.lixin.probe.util;

import com.lixin.probe.common.Result;
import com.lixin.probe.constants.SystemConstants;

/**
 * 通用验证工具类
 * 提供常用的参数验证方法，减少Controller中的重复代码
 */
public class ValidationUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ValidationUtil.class);

    /**
     * 验证ID参数
     * @param id 待验证的ID
     * @param fieldName 字段名称（用于错误提示）
     * @return 验证失败返回错误Result，验证成功返回null
     */
    public static Result<Void> validateId(Long id, String fieldName) {
        if (id == null) {
            return Result.error(fieldName + "不能为空");
        }
        if (id < 1) {
            return Result.error(fieldName + "必须为正整数");
        }
        return null;
    }

    /**
     * 验证ID参数（使用默认字段名）
     * @param id 待验证的ID
     * @return 验证失败返回错误Result，验证成功返回null
     */
    public static Result<Void> validateId(Long id) {
        return validateId(id, "ID");
    }

    /**
     * 验证分页参数
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 验证失败返回错误Result，验证成功返回null
     */
    public static Result<Void> validatePageParams(Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1) {
            return Result.error("页码必须大于0");
        }
        if (pageSize == null || pageSize < SystemConstants.Pagination.MIN_PAGE_SIZE || pageSize > SystemConstants.Pagination.MAX_PAGE_SIZE) {
            return Result.error("每页数量必须在" + SystemConstants.Pagination.MIN_PAGE_SIZE + "-" + SystemConstants.Pagination.MAX_PAGE_SIZE + "之间");
        }
        return null;
    }

    /**
     * 验证字符串参数非空
     * @param value 待验证的字符串
     * @param fieldName 字段名称
     * @return 验证失败返回错误Result，验证成功返回null
     */
    public static Result<Void> validateNotEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return Result.error(fieldName + "不能为空");
        }
        return null;
    }

    /**
     * 验证字符串参数非空（使用默认字段名）
     * @param value 待验证的字符串
     * @return 验证失败返回错误Result，验证成功返回null
     */
    public static Result<Void> validateNotEmpty(String value) {
        return validateNotEmpty(value, "参数");
    }

    /**
     * 验证集合非空
     * @param collection 集合
     * @param fieldName 字段名称
     * @return 验证失败返回错误Result，验证成功返回null
     */
    public static Result<Void> validateNotEmpty(java.util.Collection<?> collection, String fieldName) {
        if (collection == null || collection.isEmpty()) {
            return Result.error(fieldName + "不能为空");
        }
        return null;
    }

    /**
     * 验证集合大小
     * @param collection 集合
     * @param fieldName 字段名称
     * @param maxSize 最大允许的大小
     * @return 验证失败返回错误Result，验证成功返回null
     */
    public static Result<Void> validateCollectionSize(java.util.Collection<?> collection, String fieldName, int maxSize) {
        Result<Void> emptyResult = validateNotEmpty(collection, fieldName);
        if (emptyResult != null) {
            return emptyResult;
        }
        if (collection.size() > maxSize) {
            return Result.error(fieldName + "数量不能超过" + maxSize);
        }
        return null;
    }

    /**
     * 验证字符串长度
     * @param value 字符串
     * @param fieldName 字段名称
     * @param minLength 最小长度
     * @param maxLength 最大长度
     * @return 验证失败返回错误Result，验证成功返回null
     */
    public static Result<Void> validateLength(String value, String fieldName, int minLength, int maxLength) {
        if (value == null) {
            return Result.error(fieldName + "不能为空");
        }
        if (value.length() < minLength) {
            return Result.error(fieldName + "长度不能小于" + minLength);
        }
        if (value.length() > maxLength) {
            return Result.error(fieldName + "长度不能大于" + maxLength);
        }
        return null;
    }

    /**
     * 验证数值范围
     * @param value 数值
     * @param fieldName 字段名称
     * @param min 最小值
     * @param max 最大值
     * @return 验证失败返回错误Result，验证成功返回null
     */
    public static Result<Void> validateRange(Integer value, String fieldName, int min, int max) {
        if (value == null) {
            return Result.error(fieldName + "不能为空");
        }
        if (value < min || value > max) {
            return Result.error(fieldName + "必须在" + min + "-" + max + "之间");
        }
        return null;
    }

    /**
     * 验证正整数
     * @param value 数值
     * @param fieldName 字段名称
     * @return 验证失败返回错误Result，验证成功返回null
     */
    public static Result<Void> validatePositive(Long value, String fieldName) {
        if (value == null) {
            return Result.error(fieldName + "不能为空");
        }
        if (value < 1) {
            return Result.error(fieldName + "必须为正整数");
        }
        return null;
    }

    /**
     * 批量验证，遇到第一个错误即返回
     * @param results 验证结果数组
     * @return 第一个错误，如果全部验证通过返回null
     */
    @SafeVarargs
    public static Result<Void> validateAny(Result<Void>... results) {
        for (Result<Void> result : results) {
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
