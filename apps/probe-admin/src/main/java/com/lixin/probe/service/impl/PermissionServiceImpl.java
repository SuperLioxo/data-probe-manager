package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lixin.probe.entity.Permission;
import com.lixin.probe.mapper.PermissionMapper;
import com.lixin.probe.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限服务实现类（带缓存）
 */
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PermissionServiceImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Cacheable(value = "userPermissions", key = "#userId", unless = "#result.isEmpty()")
    public List<Permission> getPermissionsByUserId(Long userId) {
        log.debug("查询用户权限: userId={}", userId);
        return jdbcTemplate.query(
            "SELECT p.id, p.permission_name, p.permission_code, p.resource_type, p.resource_identifier, p.action, p.description, p.create_time, p.update_time " +
            "FROM sys_permission p " +
            "JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = ?",
            (rs, rowNum) -> {
                Permission p = new Permission();
                p.setId(rs.getLong("id"));
                p.setPermissionName(rs.getString("permission_name"));
                p.setPermissionCode(rs.getString("permission_code"));
                p.setResourceType(rs.getString("resource_type"));
                p.setResourceIdentifier(rs.getString("resource_identifier"));
                p.setAction(rs.getString("action"));
                p.setDescription(rs.getString("description"));
                p.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
                p.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
                return p;
            },
            userId
        );
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
