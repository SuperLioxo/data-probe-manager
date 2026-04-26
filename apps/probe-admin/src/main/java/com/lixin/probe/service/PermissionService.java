package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lixin.probe.entity.Permission;

import java.util.List;

/**
 * 权限服务接口
 */
public interface PermissionService extends IService<Permission> {

    /**
     * 根据用户ID查询所有权限
     * @param userId 用户ID
     * @return 权限列表
     */
    List<Permission> getPermissionsByUserId(Long userId);

    /**
     * 根据资源类型查询权限
     * @param resourceType 资源类型（MENU/BUTTON/API）
     * @return 权限列表
     */
    List<Permission> getPermissionsByResourceType(String resourceType);

    /**
     * 检查用户是否拥有指定权限
     * @param userId 用户ID
     * @param permissionCode 权限编码
     * @return 是否拥有权限
     */
    boolean hasPermission(Long userId, String permissionCode);
}
