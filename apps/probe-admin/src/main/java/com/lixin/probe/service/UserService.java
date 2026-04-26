package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lixin.probe.entity.User;

import java.util.Set;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户信息
     */
    User getByUsername(String username);

    /**
     * 验证用户登录
     * @param username 用户名
     * @param password 密码
     * @return 验证成功返回用户，失败返回null
     */
    User validateLogin(String username, String password);

    /**
     * 获取用户的权限编码集合
     * @param username 用户名
     * @return 权限编码集合
     */
    Set<String> getUserPermissions(String username);
}
