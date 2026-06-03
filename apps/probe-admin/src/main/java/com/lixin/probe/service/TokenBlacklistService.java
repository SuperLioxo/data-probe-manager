package com.lixin.probe.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token黑名单服务 —— 管理已注销的JWT令牌
 *
 * <p>本服务用于实现"主动登出"功能。在无状态的JWT认证体系中，Token一旦签发便无法撤销，
 * 即使服务端也无法使其失效。Token黑名单机制通过在服务端维护一份已注销Token的列表来
 * 解决这个问题：用户退出登录时，其Token被加入黑名单，后续的请求即使携带有效Token
 * 也会被拦截器拒绝。</p>
 *
 * <h3>工作原理：</h3>
 * <pre>
 *   用户点击"退出登录"
 *     │
 *     ▼
 *   前端调用 /api/auth/logout
 *     │
 *     ▼
 *   后端将当前Token添加到黑名单（本服务）
 *     │
 *     ▼
 *   前端删除本地存储的Token
 *
 *   --- 后续使用该Token的请求 ---
 *
 *   JwtInterceptor.preHandle()
 *     │
 *     ▼
 *   调用 tokenBlacklistService.isBlacklisted(token)
 *     │
 *     ▼
 *   Token在黑名单中？ ──是──→ 返回401，要求重新登录
 * </pre>
 *
 * <h3>存储方案：</h3>
 * <p>当前使用 {@link ConcurrentHashMap} 实现内存级黑名单存储。</p>
 * <ul>
 *   <li>优点：实现简单，读写性能高，无需额外依赖</li>
 *   <li>缺点：不支持分布式部署（每个实例有独立的黑名单）、应用重启后黑名单丢失</li>
 *   <li>优化方向：可迁移到Redis实现分布式黑名单，并利用Redis的TTL特性自动清理过期Token</li>
 * </ul>
 *
 * <h3>安全说明：</h3>
 * <p>为避免内存泄漏，黑名单中存储的是Token的hashCode值（而非完整Token字符串）。
 * 这在极小概率下可能产生哈希碰撞，但对于登出场景而言，这种概率可以忽略不计。</p>
 *
 * @see com.lixin.probe.config.JwtInterceptor JwtInterceptor在认证流程中调用本服务检查黑名单
 */
@Service
public class TokenBlacklistService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TokenBlacklistService.class);

    /**
     * 黑名单Token存储集合
     *
     * <p>使用ConcurrentHashMap.newKeySet()创建线程安全的Set集合。
     * 存储的是Token的hashCode字符串，而非Token本身，原因：</p>
     * <ol>
     *   <li>节省内存 —— hashCode比完整Token短得多</li>
     *   <li>安全性 —— 即使内存被dump也不会泄露完整Token</li>
     * </ol>
     */
    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    /**
     * 将Token添加到黑名单
     *
     * <p>用户退出登录时调用。Token被加入黑名单后，即使Token尚未过期，
     * 后续使用该Token的请求也会被JwtInterceptor拒绝。</p>
     *
     * <p>存储策略：使用Token的hashCode作为存储键，而非存储完整Token字符串。
     * 这种方式在节省内存的同时，对于登出场景的准确性足够（哈希碰撞概率极低）。</p>
     *
     * @param token JWT Token字符串
     * @return true表示成功添加（Token之前不在黑名单中），false表示添加失败或已在黑名单中
     */
    public boolean addToBlacklist(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        // 使用token的hash值作为key，避免存储过长的字符串
        String tokenHash = String.valueOf(token.hashCode());
        boolean added = blacklistedTokens.add(tokenHash);  // Set.add()在元素已存在时返回false
        if (added) {
            log.info("Token已添加到黑名单: hash={}", tokenHash);
        }
        return added;
    }

    /**
     * 检查Token是否在黑名单中
     *
     * <p>由JwtInterceptor在每次请求认证时调用。如果Token在黑名单中，
     * 请求将被拒绝（返回401状态码）。</p>
     *
     * @param token JWT Token字符串
     * @return true表示Token在黑名单中（已注销），false表示不在黑名单中
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        String tokenHash = String.valueOf(token.hashCode());
        return blacklistedTokens.contains(tokenHash);
    }

    /**
     * 从黑名单中移除Token
     *
     * <p>一般情况下不需要手动移除黑名单中的Token（Token过期后会自然失效），
     * 但在特殊场景（如管理员需要恢复某个被误注销的会话）时可以使用。</p>
     *
     * @param token JWT Token字符串
     * @return true表示成功移除，false表示Token不在黑名单中或移除失败
     */
    public boolean removeFromBlacklist(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        String tokenHash = String.valueOf(token.hashCode());
        boolean removed = blacklistedTokens.remove(tokenHash);  // Set.remove()在元素不存在时返回false
        if (removed) {
            log.info("Token已从黑名单移除: hash={}", tokenHash);
        }
        return removed;
    }

    /**
     * 获取黑名单中的Token数量
     *
     * <p>可用于监控和运维场景，了解当前有多少Token处于黑名单状态。</p>
     *
     * @return 黑名单中的Token数量
     */
    public int getBlacklistedTokenCount() {
        return blacklistedTokens.size();
    }

    /**
     * 清空整个黑名单
     *
     * <p>清空后，所有之前被注销的Token将恢复有效状态（前提是Token本身尚未过期）。
     * 谨慎使用此方法。</p>
     */
    public void clearBlacklist() {
        int size = blacklistedTokens.size();
        blacklistedTokens.clear();
        log.info("已清空Token黑名单，清空前数量: {}", size);
    }

    /**
     * 清理过期的Token（可由定时任务调用）
     *
     * <p>当前实现中，Token一旦加入黑名单将永久保留在内存中，直到应用重启。
     * 这意味着黑名单的大小会随用户登出操作持续增长。</p>
     *
     * <p>优化建议：</p>
     * <ul>
     *   <li>使用Redis替代内存存储，利用Redis的TTL自动清理过期条目</li>
     *   <li>记录Token加入黑名单时的时间戳，定时任务检查并移除已过期的条目</li>
     *   <li>使用Caffeine/Guava Cache等带TTL的本地缓存替代ConcurrentHashMap</li>
     * </ul>
     */
    public void cleanupExpiredTokens() {
        // 当前实现：Token被添加到黑名单后永久有效
        // 可以根据需要添加TTL或时间戳检查
        log.debug("Token黑名单清理检查，当前数量: {}", blacklistedTokens.size());
    }
}
