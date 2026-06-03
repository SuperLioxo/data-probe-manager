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
 * JWT（JSON Web Token）工具类 —— 令牌的生成、解析与验证
 *
 * <p>本类封装了JWT令牌的完整生命周期管理，是系统认证体系的基础设施层。
 * 基于JJWT库实现，使用HMAC-SHA256对称加密算法对令牌进行签名。</p>
 *
 * <h3>双令牌机制：</h3>
 * <p>系统采用Access Token + Refresh Token的双令牌机制：</p>
 * <ul>
 *   <li><b>Access Token（访问令牌）</b> —— 短期有效（默认8小时），用于日常API请求认证。
 *       Token的payload中包含 userId、username 等用户标识信息。</li>
 *   <li><b>Refresh Token（刷新令牌）</b> —— 长期有效（默认7天），仅用于在Access Token即将过期时
 *       获取新的Access Token，避免用户频繁重新登录。</li>
 * </ul>
 *
 * <h3>Token结构（JWT三段式）：</h3>
 * <pre>
 *   Header:  { "alg": "HS256", "typ": "JWT" }
 *   Payload: { "sub": "用户名", "userId": 123, "type": "access|refresh", "iat": 签发时间, "exp": 过期时间 }
 *   Signature: HMACSHA256(base64(header) + "." + base64(payload), secretKey)
 * </pre>
 *
 * <h3>安全配置要求：</h3>
 * <ul>
 *   <li>密钥（jwt.secret）至少32个字符（256位），满足HS256算法要求</li>
 *   <li>生产环境推荐64个字符以上</li>
 *   <li>密钥通过环境变量 JWT_SECRET 注入，不应硬编码在配置文件中</li>
 * </ul>
 *
 * <h3>相关组件：</h3>
 * <ul>
 *   <li>{@link com.lixin.probe.config.JwtInterceptor} —— 在请求拦截时调用本工具类验证Token</li>
 *   <li>{@link com.lixin.probe.service.TokenBlacklistService} —— 管理已注销的Token黑名单</li>
 *   <li>{@link com.lixin.probe.config.SecurityConfigurationValidator} —— 启动时验证JWT密钥安全性</li>
 * </ul>
 *
 * @see com.lixin.probe.config.JwtInterceptor
 * @see com.lixin.probe.service.TokenBlacklistService
 */
@Component
public class JwtUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtUtil.class);

    /**
     * JWT签名密钥
     * 从配置文件或环境变量中读取，用于Token的签名和验证。
     * 配置项：jwt.secret，环境变量名：JWT_SECRET
     */
    @Value("${jwt.secret:}")
    private String secret;

    /**
     * Access Token过期时间（毫秒）
     * 默认值28800000毫秒 = 8小时，与前端token刷新机制配合使用。
     * 配置项：jwt.expiration
     */
    @Value("${jwt.expiration:28800000}")  // 默认8小时（28800000毫秒），与配置文件保持一致
    private Long expiration;

    /**
     * Refresh Token过期时间（毫秒）
     * 默认值604800000毫秒 = 7天，Refresh Token有效期较长，用于无感刷新Access Token。
     * 配置项：jwt.refresh-expiration
     */
    @Value("${jwt.refresh-expiration:604800000}")  // 默认7天（604800000毫秒）
    private Long refreshExpiration;

    /**
     * 生成JWT Token（通用方法）
     *
     * <p>生成一个包含用户基本信息的JWT令牌。Token的subject为用户名，
     * payload中包含用户ID和自定义声明（claims）。</p>
     *
     * @param userId   用户ID，存入Token的payload中
     * @param username 用户名，作为Token的subject
     * @param claims   额外的自定义声明（可为null）
     * @return 生成的JWT Token字符串
     */
    public String generateToken(Long userId, String username, Map<String, Object> claims) {
        Date now = new Date();
        // 根据当前时间 + 过期时长计算Token的过期时间点
        Date expiryDate = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
                .subject(username)          // Token主题：用户名
                .claim("userId", userId)    // 自定义声明：用户ID
                .issuedAt(now)              // 签发时间
                .expiration(expiryDate)     // 过期时间
                .signWith(getSecretKey());  // 使用密钥签名

        // 添加额外的claims（如角色、部门等扩展信息）
        if (claims != null) {
            claims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
     * 从Token中获取用户名
     *
     * <p>用户名存储在Token的subject字段中，解析Token后直接获取。</p>
     *
     * @param token JWT Token字符串
     * @return 用户名，解析失败返回null
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getSubject();  // subject字段存储的就是用户名
        } catch (Exception e) {
            log.error("从Token获取用户名失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取用户ID
     *
     * <p>用户ID作为自定义声明 "userId" 存储在Token的payload中。</p>
     *
     * @param token JWT Token字符串
     * @return 用户ID，解析失败返回null
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("userId", Long.class);  // 从自定义声明中提取用户ID
        } catch (Exception e) {
            log.error("从Token获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 验证Token是否有效
     *
     * <p>对Token进行完整验证，包括：签名验证、过期时间检查、格式检查等。
     * 任何一项验证失败都会返回false，并记录具体的失败原因。</p>
     *
     * <p>验证维度：</p>
     * <ul>
     *   <li>ExpiredJwtException —— Token已过期</li>
     *   <li>UnsupportedJwtException —— 不支持的Token格式</li>
     *   <li>MalformedJwtException —— Token格式错误（被篡改或格式不正确）</li>
     *   <li>IllegalArgumentException —— Token为空或null</li>
     *   <li>SignatureException —— 签名验证失败（密钥不匹配）</li>
     * </ul>
     *
     * @param token JWT Token字符串
     * @return true表示Token有效，false表示Token无效
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
     * 解析Token，提取Claims（声明载荷）
     *
     * <p>使用配置的密钥验证Token签名，并解析出payload部分。
     * 如果Token签名不匹配、已过期或格式错误，将抛出相应的异常。</p>
     *
     * <p>这是Token解析的核心方法，所有从Token提取信息的操作最终都通过此方法完成。</p>
     *
     * @param token JWT Token字符串
     * @return Claims对象，包含Token中所有的声明信息
     * @throws ExpiredJwtException      Token已过期
     * @throws MalformedJwtException    Token格式错误
     * @throws SignatureException       签名验证失败
     * @throws IllegalArgumentException Token为空
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())  // 使用密钥验证签名
                .build()
                .parseSignedClaims(token)    // 解析Token，验证签名和过期时间
                .getPayload();               // 提取payload部分
    }

    /**
     * 获取签名密钥
     *
     * <p>将配置的密钥字符串转换为HMAC-SHA256算法所需的SecretKey对象。
     * 密钥长度必须至少32个字符（256位），否则HS256算法无法正常工作。</p>
     *
     * <p>安全提示：</p>
     * <ul>
     *   <li>密钥应通过环境变量注入，不要硬编码在配置文件中</li>
     *   <li>生产环境推荐使用 openssl rand -base64 64 生成强随机密钥</li>
     *   <li>密钥长度不足32字符将直接抛出异常，阻止应用启动</li>
     * </ul>
     *
     * @return SecretKey对象
     * @throws IllegalStateException 如果密钥未配置或长度不足
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
     * 从Token中获取过期时间
     *
     * @param token JWT Token字符串
     * @return 过期时间Date对象，解析失败返回null
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
     * 判断Token是否已过期
     *
     * <p>将Token中的过期时间与当前时间比较，判断Token是否已经失效。</p>
     *
     * @param token JWT Token字符串
     * @return true表示已过期，false表示仍然有效（异常情况也返回true，确保安全）
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());  // 过期时间早于当前时间则已过期
        } catch (Exception e) {
            return true;  // 解析异常时视为已过期，保证安全
        }
    }

    /**
     * 生成Access Token（短期访问令牌）
     *
     * <p>与generateToken方法类似，但在payload中额外添加了 "type": "access" 标识，
     * 用于区分Access Token和Refresh Token。</p>
     *
     * <p>Access Token是前端每次API请求都要携带的令牌，有效期较短（默认8小时）。</p>
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param claims   额外的自定义声明（可为null）
     * @return Access Token字符串
     */
    public String generateAccessToken(Long userId, String username, Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("type", "access")     // 标识为Access Token
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
     * 生成Refresh Token（长期刷新令牌）
     *
     * <p>Refresh Token的有效期远长于Access Token（默认7天），专门用于在Access Token
     * 即将过期时，通过 /api/auth/refresh 接口换取新的Access Token，实现"无感刷新"。</p>
     *
     * <p>安全设计：Refresh Token不携带额外的claims信息，仅包含用户标识和类型标记，
     * 减少泄露风险。前端应将Refresh Token安全存储（如httpOnly cookie）。</p>
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return Refresh Token字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        Date now = new Date();
        // Refresh Token使用独立的、更长的过期时间
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("type", "refresh")    // 标识为Refresh Token
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 验证是否为Refresh Token
     *
     * <p>通过检查Token的 "type" 声明来判断是否为Refresh Token。
     * 在Token刷新接口中使用，确保只能使用Refresh Token（而非Access Token）来刷新。</p>
     *
     * @param token JWT Token字符串
     * @return true表示是Refresh Token，false表示不是或解析失败
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
     *
     * <p>Token类型存储在payload的 "type" 字段中，可能的值为 "access" 或 "refresh"。</p>
     *
     * @param token JWT Token字符串
     * @return Token类型字符串（"access" 或 "refresh"），解析失败返回null
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
     * 检查Token是否即将过期
     *
     * <p>用于前端的Token自动刷新机制。前端在每次API请求前可调用此方法（或后端在响应中附带）
     * 判断当前Token是否在指定分钟内即将过期，如果是则主动使用Refresh Token获取新Token。</p>
     *
     * <p>使用示例：isTokenExpiringSoon(token, 30) 表示检查Token是否在30分钟内即将过期。</p>
     *
     * @param token   JWT token
     * @param minutes 提前多少分钟判断（默认30分钟）
     * @return true表示Token即将过期，建议刷新
     */
    public boolean isTokenExpiringSoon(String token, int minutes) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            long expireTime = expiration.getTime();
            long currentTime = System.currentTimeMillis();
            // 计算阈值：将分钟转换为毫秒
            long thresholdMs = minutes * 60 * 1000L;

            // 如果剩余有效期小于等于阈值，则认为即将过期
            return (expireTime - currentTime) <= thresholdMs;
        } catch (Exception e) {
            log.warn("检查Token过期时间失败: {}", e.getMessage());
            return true; // 出错时认为需要刷新，保证安全
        }
    }

    /**
     * 获取Access Token过期时间（毫秒）
     *
     * @return Access Token过期时间毫秒数
     */
    public long getExpirationTime() {
        return expiration;
    }

    /**
     * 获取Refresh Token过期时间（毫秒）
     *
     * @return Refresh Token过期时间毫秒数
     */
    public long getRefreshExpirationTime() {
        return refreshExpiration;
    }
}
