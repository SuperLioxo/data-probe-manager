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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户服务实现类 —— 提供用户管理、登录验证、角色与权限查询的核心业务逻辑。
 *
 * <p>本类实现了 {@link UserService} 接口，并继承 MyBatis-Plus 的 {@link ServiceImpl}，
 * 自动获得基础的 CRUD 操作能力。在此基础上扩展了以下业务功能：</p>
 * <ul>
 *   <li><b>登录验证</b>：使用 BCrypt 算法进行密码比对，防止明文密码泄露</li>
 *   <li><b>权限查询</b>：基于 RBAC 模型（用户 → 用户角色 → 角色 → 角色权限 → 权限）查询用户的权限码集合</li>
 *   <li><b>角色查询</b>：通过 sys_user_role 和 sys_role 关联表查询用户的角色编码列表</li>
 * </ul>
 *
 * <p><b>RBAC 权限模型关系链：</b></p>
 * <pre>
 *   sys_user → sys_user_role → sys_role → sys_role_permission → sys_permission
 *   (用户表)   (用户角色关联)   (角色表)   (角色权限关联)         (权限表)
 * </pre>
 *
 * <p><b>默认用户数据：</b></p>
 * <ul>
 *   <li>admin / admin123 —— ROLE_ADMIN 角色，拥有 16 项权限（全部权限）</li>
 *   <li>user / 123456 —— ROLE_OPERATOR 角色，拥有 4 项只读权限</li>
 * </ul>
 *
 * @see UserService
 * @see ServiceImpl
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserServiceImpl.class);

    /**
     * BCrypt 密码编码器 —— 用于密码的哈希比对。
     * BCrypt 是一种自适应哈希算法，内置盐值（salt），每次加密结果不同，
     * 能有效抵御彩虹表攻击和暴力破解。强度因子默认为 10（2^10 轮哈希）。
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 权限服务（可选依赖） —— 用于查询用户关联的权限列表。
     * 当权限模块未启用时该 Bean 不会被注入，此时权限查询将返回空集合。
     */
    @Autowired(required = false)
    private PermissionService permissionService;

    /** JDBC 模板 —— 用于执行角色查询等原生 SQL 操作 */
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * 根据用户名查询用户信息。
     *
     * <p>使用 MyBatis-Plus 的 LambdaQueryWrapper 构建查询条件，
     * 避免 SQL 注入风险。查询条件为 sys_user 表中 username 字段等于指定值。</p>
     *
     * @param username 用户名（登录账号）
     * @return 用户实体对象；用户名不存在时返回 null
     */
    @Override
    public User getByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return this.getOne(wrapper);
    }

    /**
     * 登录验证 —— 校验用户名和密码是否匹配。
     *
     * <p>验证流程：</p>
     * <ol>
     *   <li>根据用户名查询数据库中的用户记录</li>
     *   <li>使用 BCrypt 的 {@code matches()} 方法将明文密码与数据库中的哈希密码进行比对</li>
     * </ol>
     *
     * <p><b>密码比对原理：</b>
     * BCryptPasswordEncoder.matches(明文密码, 哈希值) 会从哈希值中提取盐值，
     * 对明文密码进行相同轮次的哈希计算，然后比较两个哈希值是否一致。
     * 这样既保证了安全性，又避免了明文密码在数据库中存储。</p>
     *
     * @param username 用户名
     * @param password 明文密码（前端传入）
     * @return 验证通过返回用户实体对象；用户不存在或密码错误返回 null
     */
    @Override
    public User validateLogin(String username, String password) {
        log.debug("========== 登录验证开始 ==========");
        log.debug("用户名: {}", username);

        // 第一步：根据用户名查询用户记录
        User user = getByUsername(username);
        if (user == null) {
            log.warn("用户不存在: {}", username);
            return null;
        }

        log.debug("找到用户: {}", user.getUsername());
        log.debug("用户状态: {}", user.getStatus());

        // 第二步：使用 BCrypt 验证密码
        // matches() 方法会将前端传入的明文密码使用数据库中哈希值的盐值进行哈希，然后与存储的哈希值比对
        boolean matches = passwordEncoder.matches(password, user.getPassword());
        log.debug("密码验证结果: {}", matches);

        if (matches) {
            log.info("登录成功: {}", username);
            return user;
        }

        log.warn("密码验证失败: {}", username);
        return null;
    }

    /**
     * 获取用户权限码集合 —— 基于 RBAC 模型查询用户拥有的所有权限标识。
     *
     * <p>查询链路：</p>
     * <pre>
     *   用户(sys_user) → 用户角色关联(sys_user_role) → 角色(sys_role)
     *       → 角色权限关联(sys_role_permission) → 权限(sys_permission)
     * </pre>
     *
     * <p>最终返回的是权限码（permission_code）的集合，例如：
     * "user:view"、"user:create"、"probe:view"、"settings:edit" 等。</p>
     *
     * @param username 用户名
     * @return 权限码字符串集合（如 {"user:view", "probe:view"}）；
     *         查询异常或用户不存在时返回空集合（不会返回 null）
     */
    @Override
    public Set<String> getUserPermissions(String username) {
        try {
            // 第一步：验证用户是否存在
            User user = validateAndGetUser(username);
            if (user == null) {
                return new HashSet<>();
            }

            // 第二步：通过 PermissionService 查询用户关联的所有权限
            // 内部会执行 RBAC 多表关联查询
            if (permissionService == null) {
                log.warn("PermissionService 未注入，返回空权限列表");
                return new HashSet<>();
            }

            List<Permission> permissions = permissionService.getPermissionsByUserId(user.getId());

            // 提取权限码（permission_code）并转为 Set 去重
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
     * 获取用户角色编码列表 —— 查询用户被分配的所有角色。
     *
     * <p>通过 sys_user_role 关联表和 sys_role 角色表进行联表查询，
     * 返回用户所拥有的角色编码（role_code）列表。</p>
     *
     * <p>例如管理员用户的角色列表为 ["ROLE_ADMIN"]，
     * 普通操作员的角色列表为 ["ROLE_OPERATOR"]。</p>
     *
     * @param userId 用户 ID
     * @return 角色编码列表（如 ["ROLE_ADMIN"]）；查询异常时返回空列表
     */
    @Override
    public List<String> getUserRoles(Long userId) {
        try {
            // 联表查询：sys_user_role JOIN sys_role
            // 通过 user_id 关联找到用户的所有角色，返回 role_code 字段
            return jdbcTemplate.queryForList(
                "SELECT r.role_code FROM sys_user_role ur JOIN sys_role r ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, userId);
        } catch (Exception e) {
            log.error("获取用户角色失败: userId={}", userId, e);
            return List.of();
        }
    }

    /**
     * 内部辅助方法 —— 验证用户是否存在并返回用户对象。
     *
     * <p>根据用户名查询数据库，如果用户不存在则记录警告日志。
     * 与 {@link #getByUsername(String)} 的区别在于此方法会记录日志。</p>
     *
     * @param username 用户名
     * @return 用户对象；不存在时返回 null 并记录警告日志
     */
    private User validateAndGetUser(String username) {
        User user = getByUsername(username);
        if (user == null) {
            log.warn("用户不存在: {}", username);
        }
        return user;
    }
}
