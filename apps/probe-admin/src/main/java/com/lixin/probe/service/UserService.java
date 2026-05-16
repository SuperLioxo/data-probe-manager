package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lixin.probe.entity.User;

import java.util.List;
import java.util.Set;

/**
 * 用户服务接口 —— 定义用户管理的核心业务方法。
 *
 * <p>本接口继承 MyBatis-Plus 的 {@link IService}，自动具备基础的 CRUD 能力
 *（如 save、removeById、updateById、getById、list 等）。
 * 在此基础上扩展了登录验证、权限查询和角色查询等认证鉴权相关的业务方法。</p>
 *
 * <p><b>RBAC 权限模型关系链：</b></p>
 * <pre>
 *   sys_user → sys_user_role → sys_role → sys_role_permission → sys_permission
 * </pre>
 *
 * <p><b>实现类：</b>{@link com.lixin.probe.service.impl.UserServiceImpl}</p>
 *
 * @see com.lixin.probe.service.impl.UserServiceImpl
 * @see IService
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户名查询用户信息。
     *
     * <p>用于登录验证、权限加载等场景，通过用户名（登录账号）定位 sys_user 表中的记录。</p>
     *
     * @param username 用户名（登录账号）
     * @return 用户实体对象；用户名不存在时返回 null
     */
    User getByUsername(String username);

    /**
     * 登录验证 —— 校验用户名和密码是否匹配。
     *
     * <p>实现类内部使用 BCrypt 算法对前端传入的明文密码与数据库中的哈希密码进行比对，
     * 避免明文密码存储和传输。无论用户不存在还是密码错误，均返回 null，
     * 不区分具体原因，防止攻击者通过不同响应推断用户是否存在。</p>
     *
     * @param username 用户名
     * @param password 明文密码（前端传入）
     * @return 验证通过返回用户实体对象；用户不存在或密码错误返回 null
     */
    User validateLogin(String username, String password);

    /**
     * 获取用户权限码集合 —— 查询用户通过角色关联的所有权限标识。
     *
     * <p>基于 RBAC 模型，查询链路为：
     * 用户 → 用户角色关联(sys_user_role) → 角色(sys_role) → 角色权限关联(sys_role_permission) → 权限(sys_permission)</p>
     *
     * <p>返回的权限码用于前端菜单渲染、按钮权限控制和后端接口鉴权。</p>
     *
     * @param username 用户名
     * @return 权限码字符串集合（如 {"user:view", "probe:view", "settings:edit"}）；
     *         查询异常或用户不存在时返回空集合
     */
    Set<String> getUserPermissions(String username);

    /**
     * 获取用户角色编码列表 —— 查询用户被分配的所有角色。
     *
     * <p>通过 sys_user_role 关联表和 sys_role 角色表联查，
     * 返回角色编码列表用于前端角色判断和后端权限校验。</p>
     *
     * <p>默认角色：</p>
     * <ul>
     *   <li>ROLE_ADMIN —— 系统管理员，拥有全部 16 项权限</li>
     *   <li>ROLE_OPERATOR —— 操作员，仅拥有 4 项只读权限</li>
     * </ul>
     *
     * @param userId 用户 ID
     * @return 角色编码列表（如 ["ROLE_ADMIN"]）；查询异常时返回空列表
     */
    List<String> getUserRoles(Long userId);
}
