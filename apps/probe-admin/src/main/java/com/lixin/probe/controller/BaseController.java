package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lixin.probe.common.Result;
import com.lixin.probe.constants.ResponseCode;
import com.lixin.probe.util.ValidationUtil;

/**
 * Controller基类
 * 提供通用的CRUD操作和验证方法，减少代码重复
 *
 * @param <T> 实体类型
 * @param <S> 服务类型
 */
public abstract class BaseController<T, S extends IService<T>> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BaseController.class);

    /**
     * 获取当前Controller对应的服务实例
     * 子类必须实现此方法
     */
    protected abstract S getService();

    /**
     * 获取实体名称，用于错误消息
     * 默认返回"数据"，子类可重写
     */
    protected String getEntityName() {
        return "数据";
    }

    // ========== 通用CRUD方法 ==========

    /**
     * 根据ID查询实体
     *
     * @param id 实体ID
     * @return 查询结果
     */
    protected Result<T> getById(Long id) {
        // 验证ID
        Result<Void> validationError = ValidationUtil.validateId(id, getEntityName() + "ID");
        if (validationError != null) {
            return Result.error(validationError.getCode(), validationError.getMessage());
        }

        // 查询实体
        T entity = getService().getById(id);
        if (entity == null) {
            return Result.notFound(getEntityName() + "不存在");
        }

        return Result.success(entity);
    }

    /**
     * 分页查询实体列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    protected Result<Page<T>> list(Integer pageNum, Integer pageSize) {
        // 验证分页参数
        Result<Void> validationError = ValidationUtil.validatePageParams(pageNum, pageSize);
        if (validationError != null) {
            return Result.error(validationError.getCode(), validationError.getMessage());
        }

        // 执行分页查询
        Page<T> page = new Page<>(pageNum, pageSize);
        Page<T> result = getService().page(page);
        return Result.success(result);
    }

    /**
     * 创建实体
     *
     * @param entity 实体对象
     * @return 创建结果
     */
    protected Result<String> create(T entity) {
        boolean success = getService().save(entity);
        return success
            ? Result.success("创建成功")
            : Result.error("创建失败");
    }

    /**
     * 更新实体
     *
     * @param id     实体ID
     * @param entity 实体对象
     * @return 更新结果
     */
    protected Result<String> update(Long id, T entity) {
        // 验证ID
        Result<Void> validationError = ValidationUtil.validateId(id, getEntityName() + "ID");
        if (validationError != null) {
            return Result.error(validationError.getCode(), validationError.getMessage());
        }

        // 检查实体是否存在
        T existEntity = getService().getById(id);
        if (existEntity == null) {
            return Result.notFound(getEntityName() + "不存在");
        }

        // 设置ID并更新
        setEntityId(entity, id);
        boolean success = getService().updateById(entity);

        return success
            ? Result.success("更新成功")
            : Result.error("更新失败");
    }

    /**
     * 删除实体
     *
     * @param id 实体ID
     * @return 删除结果
     */
    protected Result<String> delete(Long id) {
        // 验证ID
        Result<Void> validationError = ValidationUtil.validateId(id, getEntityName() + "ID");
        if (validationError != null) {
            return Result.error(validationError.getCode(), validationError.getMessage());
        }

        // 检查实体是否存在
        T existEntity = getService().getById(id);
        if (existEntity == null) {
            return Result.notFound(getEntityName() + "不存在");
        }

        // 删除实体
        boolean success = getService().removeById(id);

        return success
            ? Result.success("删除成功")
            : Result.error("删除失败");
    }

    /**
     * 批量删除实体
     *
     * @param ids ID列表
     * @return 删除结果
     */
    protected Result<String> deleteBatch(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.badRequest("ID列表不能为空");
        }

        boolean success = getService().removeByIds(ids);
        return success
            ? Result.success("批量删除成功")
            : Result.error("批量删除失败");
    }

    // ========== 辅助方法 ==========

    /**
     * 设置实体ID
     * 使用反射尝试调用setId方法，如果失败则忽略
     *
     * @param entity 实体对象
     * @param id     ID值
     */
    protected void setEntityId(T entity, Long id) {
        try {
            // 尝试使用setId方法
            entity.getClass().getMethod("setId", Long.class).invoke(entity, id);
        } catch (Exception e) {
            log.warn("无法设置实体ID: {}", e.getMessage());
        }
    }

    /**
     * 验证ID并获取实体
     * 如果验证失败或实体不存在，返回错误结果
     * 如果一切正常，返回null
     *
     * @param id         实体ID
     * @param entityName 实体名称
     * @param errorResult 错误结果（输出参数）
     * @return 实体对象，如果不存在返回null
     */
    protected T validateAndGetEntity(Long id, String entityName, Result<T> errorResult) {
        // 验证ID
        Result<Void> validationError = ValidationUtil.validateId(id, entityName + "ID");
        if (validationError != null) {
            errorResult.setCode(validationError.getCode());
            errorResult.setMessage(validationError.getMessage());
            return null;
        }

        // 查询实体
        T entity = getService().getById(id);
        if (entity == null) {
            errorResult.setCode(ResponseCode.NOT_FOUND.getCode());
            errorResult.setMessage(entityName + "不存在");
            return null;
        }

        return entity;
    }

    /**
     * 检查实体是否存在
     *
     * @param id 实体ID
     * @return true如果存在，false如果不存在
     */
    protected boolean exists(Long id) {
        if (id == null || id < 1) {
            return false;
        }
        return getService().getById(id) != null;
    }

    /**
     * 成功响应
     */
    protected <R> Result<R> ok() {
        return Result.success();
    }

    /**
     * 成功响应（带数据）
     */
    protected <R> Result<R> ok(R data) {
        return Result.success(data);
    }

    /**
     * 成功响应（带消息）
     */
    protected <R> Result<R> ok(String message) {
        return Result.success(message);
    }

    /**
     * 成功响应（带消息和数据）
     */
    protected <R> Result<R> ok(String message, R data) {
        return Result.success(message, data);
    }

    /**
     * 错误响应
     */
    protected <R> Result<R> fail(String message) {
        return Result.error(message);
    }

    /**
     * 错误响应（带错误码）
     */
    protected <R> Result<R> fail(int code, String message) {
        return Result.error(code, message);
    }

    /**
     * 未找到响应
     */
    protected <R> Result<R> notFound(String message) {
        return Result.notFound(message);
    }

    /**
     * 参数错误响应
     */
    protected <R> Result<R> badRequest(String message) {
        return Result.badRequest(message);
    }

    /**
     * 权限不足响应
     */
    protected <R> Result<R> forbidden(String message) {
        return Result.forbidden(message);
    }
}
