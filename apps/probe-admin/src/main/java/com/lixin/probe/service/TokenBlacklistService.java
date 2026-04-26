package com.lixin.probe.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token黑名单服务
 * 用于管理已退出登录的Token
 */
@Service
public class TokenBlacklistService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TokenBlacklistService.class);

    // 使用ConcurrentHashMap存储黑名单Token，key为token的hash值
    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    /**
     * 将Token添加到黑名单
     *
     * @param token JWT Token
     * @return 是否成功添加
     */
    public boolean addToBlacklist(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        // 使用token的hash值作为key，避免存储过长的字符串
        String tokenHash = String.valueOf(token.hashCode());
        boolean added = blacklistedTokens.add(tokenHash);
        if (added) {
            log.info("Token已添加到黑名单: hash={}", tokenHash);
        }
        return added;
    }

    /**
     * 检查Token是否在黑名单中
     *
     * @param token JWT Token
     * @return 是否在黑名单中
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
     * @param token JWT Token
     * @return 是否成功移除
     */
    public boolean removeFromBlacklist(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        String tokenHash = String.valueOf(token.hashCode());
        boolean removed = blacklistedTokens.remove(tokenHash);
        if (removed) {
            log.info("Token已从黑名单移除: hash={}", tokenHash);
        }
        return removed;
    }

    /**
     * 获取黑名单Token数量
     *
     * @return 黑名单数量
     */
    public int getBlacklistedTokenCount() {
        return blacklistedTokens.size();
    }

    /**
     * 清空黑名单
     */
    public void clearBlacklist() {
        int size = blacklistedTokens.size();
        blacklistedTokens.clear();
        log.info("已清空Token黑名单，清空前数量: {}", size);
    }

    /**
     * 清理过期的Token（可以由定时任务调用）
     * 当前实现为永久黑名单，Token过期后仍在黑名单中
     * 如果需要自动清理过期Token，需要在这里添加逻辑
     */
    public void cleanupExpiredTokens() {
        // 当前实现：Token被添加到黑名单后永久有效
        // 可以根据需要添加TTL或时间戳检查
        log.debug("Token黑名单清理检查，当前数量: {}", blacklistedTokens.size());
    }
}
