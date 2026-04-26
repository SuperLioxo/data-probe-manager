package com.lixin.probe.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类
 */
@Component
public class JwtUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration:28800000}")  // 默认8小时（28800000毫秒），与配置文件保持一致
    private Long expiration;

    @Value("${jwt.refresh-expiration:604800000}")  // 默认7天（604800000毫秒）
    private Long refreshExpiration;

    /**
     * 生成JWT Token
     */
    public String generateToken(Long userId, String username, Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey());

        // 添加额外的claims
        if (claims != null) {
            claims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("从Token获取用户名失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            log.error("从Token获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的Token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Token格式错误: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Token为空: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Token验证失败", e);
        }
        return false;
    }

    /**
     * 解析Token
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取密钥
     */
    private SecretKey getSecretKey() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("JWT密钥未配置！请在环境变量中设置JWT_SECRET，或者使用至少256位的随机密钥");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT密钥长度不足！当前长度: " + secret.length() + "，要求至少32个字符（256位）");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取Token过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration();
        } catch (Exception e) {
            log.error("从Token获取过期时间失败", e);
            return null;
        }
    }

    /**
     * 判断Token是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 生成Access Token (短期token)
     */
    public String generateAccessToken(Long userId, String username, Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey());

        // 添加额外的claims
        if (claims != null) {
            claims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
     * 生成Refresh Token (长期token，用于刷新access token)
     */
    public String generateRefreshToken(Long userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 验证是否为Refresh Token
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            String type = claims.get("type", String.class);
            return "refresh".equals(type);
        } catch (Exception e) {
            log.warn("验证Refresh Token失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从Token中获取Token类型
     */
    public String getTokenType(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("type", String.class);
        } catch (Exception e) {
            log.error("从Token获取类型失败", e);
            return null;
        }
    }

    /**
     * 检查Token是否即将过期（在指定分钟内）
     * @param token JWT token
     * @param minutes 提前多少分钟（默认30分钟）
     * @return true如果即将过期
     */
    public boolean isTokenExpiringSoon(String token, int minutes) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            long expireTime = expiration.getTime();
            long currentTime = System.currentTimeMillis();
            long thresholdMs = minutes * 60 * 1000L;

            return (expireTime - currentTime) <= thresholdMs;
        } catch (Exception e) {
            log.warn("检查Token过期时间失败: {}", e.getMessage());
            return true; // 出错时认为需要刷新
        }
    }

    /**
     * 获取Access Token过期时间（毫秒）
     */
    public long getExpirationTime() {
        return expiration;
    }

    /**
     * 获取Refresh Token过期时间（毫秒）
     */
    public long getRefreshExpirationTime() {
        return refreshExpiration;
    }
}
