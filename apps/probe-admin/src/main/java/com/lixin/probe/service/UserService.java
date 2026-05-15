package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lixin.probe.entity.User;

import java.util.List;
import java.util.Set;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    User getByUsername(String username);

    User validateLogin(String username, String password);

    Set<String> getUserPermissions(String username);

    List<String> getUserRoles(Long userId);
}
