package com.lixin.probe.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SensitiveDataUtils 单元测试
 * 测试敏感数据脱敏功能
 *
 * @author Claude Code
 * @date 2026-04-12
 */
@DisplayName("敏感数据脱敏工具测试")
class SensitiveDataUtilsTest {

    @Test
    @DisplayName("测试密码脱敏")
    void testSanitizePassword() {
        String input = "password=secret123 and pwd=test456";
        String result = SensitiveDataUtils.sanitize(input);

        assertEquals("password=*** and pwd=***", result);
        assertFalse(result.contains("secret123"));
        assertFalse(result.contains("test456"));
    }

    @Test
    @DisplayName("测试Token脱敏")
    void testSanitizeToken() {
        String input = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
        String result = SensitiveDataUtils.sanitize(input);

        assertEquals("Bearer ***", result);
        assertFalse(result.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
    }

    @Test
    @DisplayName("测试连接字符串脱敏")
    void testSanitizeConnectionString() {
        String input = "postgresql://user:password@localhost:5432/db";
        String result = SensitiveDataUtils.sanitize(input);

        assertEquals("postgresql://user:***@localhost:5432/db", result);
        assertFalse(result.contains(":password@"));
    }

    @Test
    @DisplayName("测试邮箱脱敏")
    void testSanitizeEmail() {
        String input = "user@example.com";
        String result = SensitiveDataUtils.sanitize(input);

        assertEquals("user***@example.com", result);
    }

    @Test
    @DisplayName("测试手机号脱敏")
    void testSanitizePhone() {
        String input = "手机号：13812345678";
        String result = SensitiveDataUtils.sanitize(input);

        assertEquals("手机号：138******5678", result);
        assertFalse(result.contains("1234"));
    }

    @Test
    @DisplayName("测试多种敏感信息混合脱敏")
    void testSanitizeMixedSensitiveData() {
        String input = "password=secret123 token=abc email=test@example.com phone=13812345678";
        String result = SensitiveDataUtils.sanitize(input);

        // 验证所有敏感信息都被脱敏
        assertFalse(result.contains("secret123"));
        assertFalse(result.contains("abc"));
        assertFalse(result.contains("test@example.com"));
        assertFalse(result.contains("1234"));
    }

    @Test
    @DisplayName("测试空字符串输入")
    void testSanitizeEmptyString() {
        String input = "";
        String result = SensitiveDataUtils.sanitize(input);

        assertEquals("", result);
    }

    @Test
    @DisplayName("测试null输入")
    void testSanitizeNull() {
        String result = SensitiveDataUtils.sanitize(null);

        assertNull(result);
    }

    @Test
    @DisplayName("测试无敏感信息字符串")
    void testSanitizeNonSensitiveData() {
        String input = "This is a normal message without sensitive data";
        String result = SensitiveDataUtils.sanitize(input);

        assertEquals(input, result);
    }

    @Test
    @DisplayName("测试对象脱敏")
    void testSanitizeObject() {
        Object obj = new Object() {
            @Override
            public String toString() {
                return "password=secret123";
            }
        };

        String result = SensitiveDataUtils.sanitize(obj);

        assertEquals("password=***", result);
    }

    @Test
    @DisplayName("测试null对象脱敏")
    void testSanitizeNullObject() {
        String result = SensitiveDataUtils.sanitize((Object) null);

        assertEquals("null", result);
    }

    @Test
    @DisplayName("测试检测敏感信息")
    void testContainsSensitiveData() {
        assertTrue(SensitiveDataUtils.containsSensitiveData("password=secret"));
        assertTrue(SensitiveDataUtils.containsSensitiveData("Bearer token"));
        assertTrue(SensitiveDataUtils.containsSensitiveData("postgresql://user:pass@host"));

        assertFalse(SensitiveDataUtils.containsSensitiveData("normal message"));
        assertFalse(SensitiveDataUtils.containsSensitiveData(""));
        assertFalse(SensitiveDataUtils.containsSensitiveData(null));
    }

    @Test
    @DisplayName("测试IP地址脱敏")
    void testMaskIpAddress() {
        String input = "192.168.1.100";
        String result = SensitiveDataUtils.maskIpAddress(input);

        assertEquals("192.168.***.100", result);
    }

    @Test
    @DisplayName("测试用户名脱敏")
    void testMaskUsername() {
        assertEquals("a***e", SensitiveDataUtils.maskUsername("alice"));
        assertEquals("b***", SensitiveDataUtils.maskUsername("bob"));
        assertEquals("a***", SensitiveDataUtils.maskUsername("ab"));
    }

    @Test
    @DisplayName("测试银行卡号脱敏")
    void testMaskCardNumber() {
        String input = "6222021234567890123";
        String result = SensitiveDataUtils.maskCardNumber(input);

        assertEquals("6222***********0123", result);
    }

    @Test
    @DisplayName("测试日志消息格式化脱敏")
    void testSanitizeLogMessage() {
        String format = "用户登录: username=%s, password=%s";
        Object[] args = {"admin", "secret123"};

        String result = SensitiveDataUtils.sanitizeLogMessage(format, args);

        assertTrue(result.contains("admin"));
        assertFalse(result.contains("secret123"));
    }
}
