package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lixin.probe.entity.Permission;
import com.lixin.probe.entity.User;
import com.lixin.probe.mapper.UserMapper;
import com.lixin.probe.service.PermissionService;
import com.lixin.probe.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserServiceImpl.class);

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired(required = false)
    private PermissionService permissionService;

    @Override
    public User getByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return this.getOne(wrapper);
    }

    @Override
    public User validateLogin(String username, String password) {
        log.debug("========== 登录验证开始 ==========");
        log.debug("用户名: {}", username);

        User user = getByUsername(username);
        if (user == null) {
            log.warn("用户不存在: {}", username);
            return null;
        }

        log.debug("找到用户: {}", user.getUsername());
        log.debug("用户状态: {}", user.getStatus());

        // 使用 BCrypt 验证密码
        boolean matches = passwordEncoder.matches(password, user.getPassword());
        log.debug("密码验证结果: {}", matches);

        if (matches) {
            log.info("登录成功: {}", username);
            return user;
        }

        log.warn("密码验证失败: {}", username);
        return null;
    }

    @Override
    public Set<String> getUserPermissions(String username) {
        try {
            // 1. 验证用户存在
            User user = validateAndGetUser(username);
            if (user == null) {
                return new HashSet<>();
            }

            // 2. 直接使用PermissionService查询用户权限
            if (permissionService == null) {
                log.warn("PermissionService 未注入，返回空权限列表");
                return new HashSet<>();
            }

            List<Permission> permissions = permissionService.getPermissionsByUserId(user.getId());
            Set<String> permissionCodes = permissions.stream()
                    .map(Permission::getPermissionCode)
                    .collect(Collectors.toSet());

            log.debug("用户 {} 的权限: {}", username, permissionCodes);
            return permissionCodes;

        } catch (Exception e) {
            log.error("获取用户权限失败: {}", username, e);
            return new HashSet<>();
        }
    }

    /**
     * 验证用户是否存在并返回用户对象
     *
     * @param username 用户名
     * @return 用户对象，不存在返回null
     */
    private User validateAndGetUser(String username) {
        User user = getByUsername(username);
        if (user == null) {
            log.warn("用户不存在: {}", username);
        }
        return user;
    }
}
