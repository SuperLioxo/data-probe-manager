package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lixin.probe.common.Result;
import com.lixin.probe.constants.SystemConstants;
import com.lixin.probe.constants.ResponseCode;
import com.lixin.probe.util.ValidationUtil;

/**
 * Controller辅助工具类
 * 提供通用的CRUD操作和验证方法，减少代码重复
 *
 * 使用方式：
 * <pre>
 * {@code
 * @RestController
 * @RequestMapping("/api/users")
 * public class UserController {
 *     @Autowired
 *     private UserService userService;
 *
 *     @GetMapping("/{id}")
 *     public Result<User> getById(@PathVariable Long id) {
 *         return ControllerHelper.getById(userService, id, "用户");
 *     }
 *
 *     @GetMapping
 *     public Result<Page<User>> list(
 *             @RequestParam(defaultValue = "1") Integer pageNum,
 *             @RequestParam(defaultValue = "10") Integer pageSize) {
 *         return ControllerHelper.list(userService, pageNum, pageSize);
 *     }
 * }
 * }
 * </pre>
 */
public class ControllerHelper {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ControllerHelper.class);

    // ========== 通用CRUD方法 ==========

    /**
     * 根据ID查询实体
     *
     * @param service    服务实例
     * @param id         实体ID
     * @param entityName 实体名称（用于错误消息）
     * @param <T>        实体类型
     * @return 查询结果
     */
    public static <T> Result<T> getById(IService<T> service, Long id, String entityName) {
        // 验证ID
        Result<Void> validationError = ValidationUtil.validateId(id, entityName + "ID");
        if (validationError != null) {
            return Result.error(validationError.getCode(), validationError.getMessage());
        }

        // 查询实体
        T entity = service.getById(id);
        if (entity == null) {
            return Result.notFound(entityName + "不存在");
        }

        return Result.success(entity);
    }

    /**
     * 根据ID查询实体（使用默认实体名称"数据"）
     *
     * @param service 服务实例
     * @param id      实体ID
     * @param <T>     实体类型
     * @return 查询结果
     */
    public static <T> Result<T> getById(IService<T> service, Long id) {
        return getById(service, id, "数据");
    }

    /**
     * 分页查询实体列表
     *
     * @param service  服务实例
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param <T>      实体类型
     * @return 分页结果
     */
    public static <T> Result<Page<T>> list(IService<T> service, Integer pageNum, Integer pageSize) {
        // 验证分页参数
        Result<Void> validationError = ValidationUtil.validatePageParams(pageNum, pageSize);
        if (validationError != null) {
            return Result.error(validationError.getCode(), validationError.getMessage());
        }

        // 执行分页查询
        Page<T> page = new Page<>(pageNum, pageSize);
        Page<T> result = service.page(page);
        return Result.success(result);
    }

    /**
     * 创建实体
     *
     * @param service    服务实例
     * @param entity     实体对象
     * @param entityName 实体名称（用于成功消息）
     * @param <T>        实体类型
     * @return 创建结果
     */
    public static <T> Result<String> create(IService<T> service, T entity, String entityName) {
        boolean success = service.save(entity);
        return success
            ? Result.success(entityName + "创建成功")
            : Result.error(entityName + "创建失败");
    }

    /**
     * 创建实体（使用默认实体名称"数据"）
     *
     * @param service 服务实例
     * @param entity  实体对象
     * @param <T>     实体类型
     * @return 创建结果
     */
    public static <T> Result<String> create(IService<T> service, T entity) {
        return create(service, entity, "数据");
    }

    /**
     * 更新实体
     *
     * @param service    服务实例
     * @param id         实体ID
     * @param entity     实体对象
     * @param entityName 实体名称（用于错误消息）
     * @param <T>        实体类型
     * @return 更新结果
     */
    public static <T> Result<String> update(IService<T> service, Long id, T entity, String entityName) {
        // 验证ID
        Result<Void> validationError = ValidationUtil.validateId(id, entityName + "ID");
        if (validationError != null) {
            return Result.error(validationError.getCode(), validationError.getMessage());
        }

        // 检查实体是否存在
        T existEntity = service.getById(id);
        if (existEntity == null) {
            return Result.notFound(entityName + "不存在");
        }

        // 设置ID并更新
        setEntityId(entity, id);
        boolean success = service.updateById(entity);

        return success
            ? Result.success(entityName + "更新成功")
            : Result.error(entityName + "更新失败");
    }

    /**
     * 更新实体（使用默认实体名称"数据"）
     *
     * @param service 服务实例
     * @param id      实体ID
     * @param entity  实体对象
     * @param <T>     实体类型
     * @return 更新结果
     */
    public static <T> Result<String> update(IService<T> service, Long id, T entity) {
        return update(service, id, entity, "数据");
    }

    /**
     * 删除实体
     *
     * @param service    服务实例
     * @param id         实体ID
     * @param entityName 实体名称（用于错误消息）
     * @param <T>        实体类型
     * @return 删除结果
     */
    public static <T> Result<String> delete(IService<T> service, Long id, String entityName) {
        // 验证ID
        Result<Void> validationError = ValidationUtil.validateId(id, entityName + "ID");
        if (validationError != null) {
            return Result.error(validationError.getCode(), validationError.getMessage());
        }

        // 检查实体是否存在
        T existEntity = service.getById(id);
        if (existEntity == null) {
            return Result.notFound(entityName + "不存在");
        }

        // 删除实体
        boolean success = service.removeById(id);

        return success
            ? Result.success(entityName + "删除成功")
            : Result.error(entityName + "删除失败");
    }

    /**
     * 删除实体（使用默认实体名称"数据"）
     *
     * @param service 服务实例
     * @param id      实体ID
     * @param <T>     实体类型
     * @return 删除结果
     */
    public static <T> Result<String> delete(IService<T> service, Long id) {
        return delete(service, id, "数据");
    }

    /**
     * 批量删除实体
     *
     * @param service    服务实例
     * @param ids        ID列表
     * @param entityName 实体名称（用于错误消息）
     * @param <T>        实体类型
     * @return 删除结果
     */
    public static <T> Result<String> deleteBatch(IService<T> service, java.util.List<Long> ids, String entityName) {
        if (ids == null || ids.isEmpty()) {
            return Result.badRequest("ID列表不能为空");
        }

        boolean success = service.removeByIds(ids);
        return success
            ? Result.success(entityName + "批量删除成功")
            : Result.error(entityName + "批量删除失败");
    }

    /**
     * 批量删除实体（使用默认实体名称"数据"）
     *
     * @param service 服务实例
     * @param ids     ID列表
     * @param <T>     实体类型
     * @return 删除结果
     */
    public static <T> Result<String> deleteBatch(IService<T> service, java.util.List<Long> ids) {
        return deleteBatch(service, ids, "数据");
    }

    // ========== 辅助方法 ==========

    /**
     * 设置实体ID
     * 使用反射尝试调用setId方法，如果失败则忽略
     *
     * @param entity 实体对象
     * @param id     ID值
     * @param <T>    实体类型
     */
    public static <T> void setEntityId(T entity, Long id) {
        if (entity == null || id == null) {
            return;
        }

        try {
            // 尝试使用setId方法
            entity.getClass().getMethod("setId", Long.class).invoke(entity, id);
        } catch (Exception e) {
            log.debug("无法设置实体ID: {}", e.getMessage());
        }
    }

    /**
     * 检查实体是否存在
     *
     * @param service 服务实例
     * @param id      实体ID
     * @param <T>     实体类型
     * @return true如果存在，false如果不存在
     */
    public static <T> boolean exists(IService<T> service, Long id) {
        if (id == null || id < 1) {
            return false;
        }
        return service.getById(id) != null;
    }

    /**
     * 验证批量操作的大小
     *
     * @param collection     集合
     * @param collectionName 集合名称
     * @param maxSize        最大数量
     * @return 验证结果，null表示验证通过
     */
    public static Result<Void> validateBatchSize(java.util.Collection<?> collection, String collectionName, int maxSize) {
        return ValidationUtil.validateCollectionSize(collection, collectionName, maxSize);
    }

    /**
     * 验证批量操作的大小（使用默认最大数量）
     *
     * @param collection     集合
     * @param collectionName 集合名称
     * @return 验证结果，null表示验证通过
     */
    public static Result<Void> validateBatchSize(java.util.Collection<?> collection, String collectionName) {
        return validateBatchSize(collection, collectionName, SystemConstants.Limits.MAX_BATCH_SIZE);
    }

    // ========== 响应辅助方法 ==========

    /**
     * 构建批量操作结果
     *
     * @param total   总数量
     * @param success 成功数量
     * @param items   成功的项目列表
     * @param <T>     项目类型
     * @return 结果Map
     */
    public static <T> java.util.Map<String, Object> buildBatchResult(int total, int success, java.util.List<T> items) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("failed", total - success);
        if (items != null) {
            result.put("items", items);
        }
        return result;
    }

    /**
     * 构建批量操作结果（不包含项目列表）
     *
     * @param total   总数量
     * @param success 成功数量
     * @return 结果Map
     */
    public static java.util.Map<String, Object> buildBatchResult(int total, int success) {
        return buildBatchResult(total, success, null);
    }
}
