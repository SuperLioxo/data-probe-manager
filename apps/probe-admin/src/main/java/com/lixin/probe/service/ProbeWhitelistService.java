package com.lixin.probe.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 探针白名单服务（基于Redis）
 * 用于控制探针自动注册的安全性
 */
@Service
public class ProbeWhitelistService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeWhitelistService.class);

    private static final String WHITELIST_KEY = "probe:whitelist";
    private static final String IP_WHITELIST_PREFIX = "probe:ip:whitelist:";

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 检查探针是否在白名单中
     *
     * @param probeKey 探针标识
     * @return 是否在白名单中
     */
    public boolean isWhitelisted(String probeKey) {
        if (redisTemplate == null) {
            log.warn("Redis未配置，白名单功能不可用，默认拒绝所有探针注册");
            return false;
        }

        try {
            Boolean isMember = redisTemplate.opsForSet().isMember(WHITELIST_KEY, probeKey);
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.error("检查探针白名单失败: probeKey={}", probeKey, e);
            return false;
        }
    }

    /**
     * 检查IP是否在指定探针的白名单中
     *
     * @param probeKey 探针标识
     * @param ip IP地址
     * @return 是否在白名单中
     */
    public boolean isIpWhitelistedForProbe(String probeKey, String ip) {
        if (redisTemplate == null) {
            log.warn("Redis未配置，IP白名单功能不可用，默认拒绝");
            return false;
        }

        try {
            String key = IP_WHITELIST_PREFIX + probeKey;
            Boolean isMember = redisTemplate.opsForSet().isMember(key, ip);
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.error("检查IP白名单失败: probeKey={}, ip={}", probeKey, ip, e);
            return false;
        }
    }

    /**
     * 添加探针到白名单
     *
     * @param probeKey 探针标识
     */
    public void addToWhitelist(String probeKey) {
        if (redisTemplate == null) {
            log.warn("Redis未配置，无法添加探针到白名单: probeKey={}", probeKey);
            return;
        }

        try {
            redisTemplate.opsForSet().add(WHITELIST_KEY, probeKey);
            log.info("探针已添加到白名单: probeKey={}", probeKey);
        } catch (Exception e) {
            log.error("添加探针到白名单失败: probeKey={}", probeKey, e);
        }
    }

    /**
     * 从白名单移除探针
     *
     * @param probeKey 探针标识
     */
    public void removeFromWhitelist(String probeKey) {
        if (redisTemplate == null) {
            log.warn("Redis未配置，无法从白名单移除探针: probeKey={}", probeKey);
            return;
        }

        try {
            redisTemplate.opsForSet().remove(WHITELIST_KEY, probeKey);
            log.info("探针已从白名单移除: probeKey={}", probeKey);
        } catch (Exception e) {
            log.error("从白名单移除探针失败: probeKey={}", probeKey, e);
        }
    }

    /**
     * 添加IP到指定探针的白名单
     *
     * @param probeKey 探针标识
     * @param ip IP地址
     */
    public void addIpToWhitelist(String probeKey, String ip) {
        if (redisTemplate == null) {
            log.warn("Redis未配置，无法添加IP到白名单: probeKey={}, ip={}", probeKey, ip);
            return;
        }

        try {
            String key = IP_WHITELIST_PREFIX + probeKey;
            redisTemplate.opsForSet().add(key, ip);
            // 设置30天过期
            redisTemplate.expire(key, Duration.ofDays(30));
            log.info("IP已添加到白名单: probeKey={}, ip={}", probeKey, ip);
        } catch (Exception e) {
            log.error("添加IP到白名单失败: probeKey={}, ip={}", probeKey, ip, e);
        }
    }

    /**
     * 从指定探针的白名单移除IP
     *
     * @param probeKey 探针标识
     * @param ip IP地址
     */
    public void removeIpFromWhitelist(String probeKey, String ip) {
        if (redisTemplate == null) {
            log.warn("Redis未配置，无法从白名单移除IP: probeKey={}, ip={}", probeKey, ip);
            return;
        }

        try {
            String key = IP_WHITELIST_PREFIX + probeKey;
            redisTemplate.opsForSet().remove(key, ip);
            log.info("IP已从白名单移除: probeKey={}, ip={}", probeKey, ip);
        } catch (Exception e) {
            log.error("从白名单移除IP失败: probeKey={}, ip={}", probeKey, ip, e);
        }
    }

    /**
     * 获取所有白名单探针
     *
     * @return 白名单探针集合
     */
    public Set<String> getAllWhitelistedProbes() {
        if (redisTemplate == null) {
            log.warn("Redis未配置，无法获取白名单探针列表");
            return Set.of();
        }

        try {
            Set<String> members = redisTemplate.opsForSet().members(WHITELIST_KEY);
            return members != null ? members : Set.of();
        } catch (Exception e) {
            log.error("获取白名单探针列表失败", e);
            return Set.of();
        }
    }

    /**
     * 获取指定探针的所有白名单IP
     *
     * @param probeKey 探针标识
     * @return 白名单IP集合
     */
    public Set<String> getWhitelistedIps(String probeKey) {
        if (redisTemplate == null) {
            log.warn("Redis未配置，无法获取白名单IP列表: probeKey={}", probeKey);
            return Set.of();
        }

        try {
            String key = IP_WHITELIST_PREFIX + probeKey;
            Set<String> members = redisTemplate.opsForSet().members(key);
            return members != null ? members : Set.of();
        } catch (Exception e) {
            log.error("获取白名单IP列表失败: probeKey={}", probeKey, e);
            return Set.of();
        }
    }

    /**
     * 清空所有白名单
     */
    public void clearAllWhitelists() {
        if (redisTemplate == null) {
            log.warn("Redis未配置，无法清空白名单");
            return;
        }

        try {
            // 删除探针白名单
            redisTemplate.delete(WHITELIST_KEY);

            // 删除所有IP白名单
            Set<String> keys = redisTemplate.keys(IP_WHITELIST_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            log.info("已清空所有白名单");
        } catch (Exception e) {
            log.error("清空白名单失败", e);
        }
    }

    /**
     * 检查Redis是否可用
     *
     * @return Redis是否可用
     */
    public boolean isRedisAvailable() {
        return redisTemplate != null;
    }
}
