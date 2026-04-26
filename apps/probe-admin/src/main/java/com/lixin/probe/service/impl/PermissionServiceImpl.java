package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lixin.probe.entity.Permission;
import com.lixin.probe.mapper.PermissionMapper;
import com.lixin.probe.service.PermissionService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限服务实现类（带缓存）
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (添加缓存优化)
 */
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PermissionServiceImpl.class);

    @Override
    @Cacheable(value = "userPermissions", key = "#userId", unless = "#result.isEmpty()")
    public List<Permission> getPermissionsByUserId(Long userId) {
        log.debug("查询用户权限: userId={} (返回所有可用权限)", userId);
        return this.list();
    }

    @Override
    public List<Permission> getPermissionsByResourceType(String resourceType) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getResourceType, resourceType);
        return this.list(wrapper);
    }

    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        if (permissionCode == null || permissionCode.isEmpty()) {
            log.warn("权限码为空: userId={}", userId);
            return false;
        }

        List<Permission> permissions = getPermissionsByUserId(userId);

        if (permissions == null || permissions.isEmpty()) {
            return false;
        }

        return permissions.stream()
                .filter(p -> p != null && p.getPermissionCode() != null)
                .anyMatch(p -> p.getPermissionCode().equals(permissionCode));
    }
}
