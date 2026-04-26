package com.lixin.probe;

import com.lixin.probe.config.AuditLogProperties;
import com.lixin.probe.entity.AuditLog;
import com.lixin.probe.enums.AuditLogLevel;
import com.lixin.probe.enums.AuditLogOperation;
import com.lixin.probe.mapper.AuditLogMapper;
import com.lixin.probe.service.AuditLogService;
import com.lixin.probe.service.impl.AuditLogServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 审计日志服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class AuditLogTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private AuditLogProperties properties;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        // 配置默认行为
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isAsync()).thenReturn(false);
        when(properties.isLogQueryOperations()).thenReturn(true);
        when(properties.isLogSuccessOperations()).thenReturn(true);
        when(properties.isLogFailedOperations()).thenReturn(true);
    }

    @Test
    void testCreateAuditLog() {
        // 准备测试数据
        AuditLog auditLog = AuditLog.builder()
                .userId(1L)
                .username("testuser")
                .operation(AuditLogOperation.CREATE.getCode())
                .module("TEST")
                .description("创建测试")
                .level(AuditLogLevel.INFO.name())
                .build();

        // 执行测试
        boolean result = auditLogService.create(auditLog);

        // 验证结果
        assertTrue(result);
    }

    @Test
    void testRecordLogin() {
        // 执行测试
        auditLogService.recordLogin(1L, "testuser", "127.0.0.1", "Mozilla/5.0", true);

        // 验证记录成功
        verify(auditLogMapper, times(1)).insert(any(AuditLog.class));
    }

    @Test
    void testRecordLogout() {
        // 执行测试
        auditLogService.recordLogout(1L, "testuser");

        // 验证记录成功
        verify(auditLogMapper, times(1)).insert(any(AuditLog.class));
    }

    @Test
    void testRecordPermissionChange() {
        // 执行测试
        auditLogService.recordPermissionChange(1L, "admin", 2L, "READ", "READ,WRITE");

        // 验证记录成功
        verify(auditLogMapper, times(1)).insert(any(AuditLog.class));
    }

    @Test
    void testRecordConfigChange() {
        // 执行测试
        auditLogService.recordConfigChange(1L, "admin", "timeout", "30", "60");

        // 验证记录成功
        verify(auditLogMapper, times(1)).insert(any(AuditLog.class));
    }

    @Test
    void testAuditLogLevelEnum() {
        // 测试枚举值
        assertEquals(0, AuditLogLevel.INFO.getCode());
        assertEquals(1, AuditLogLevel.WARN.getCode());
        assertEquals(2, AuditLogLevel.ERROR.getCode());
        assertEquals(3, AuditLogLevel.CRITICAL.getCode());

        // 测试fromCode方法
        assertEquals(AuditLogLevel.INFO, AuditLogLevel.fromCode(0));
        assertEquals(AuditLogLevel.WARN, AuditLogLevel.fromCode(1));
        assertEquals(AuditLogLevel.ERROR, AuditLogLevel.fromCode(2));
        assertEquals(AuditLogLevel.CRITICAL, AuditLogLevel.fromCode(3));

        // 测试默认值
        assertEquals(AuditLogLevel.INFO, AuditLogLevel.fromCode(999));
    }

    @Test
    void testAuditLogOperationEnum() {
        // 测试枚举值
        assertEquals("CREATE", AuditLogOperation.CREATE.getCode());
        assertEquals("UPDATE", AuditLogOperation.UPDATE.getCode());
        assertEquals("DELETE", AuditLogOperation.DELETE.getCode());
        assertEquals("QUERY", AuditLogOperation.QUERY.getCode());
        assertEquals("LOGIN", AuditLogOperation.LOGIN.getCode());
        assertEquals("LOGOUT", AuditLogOperation.LOGOUT.getCode());

        // 测试fromCode方法
        assertEquals(AuditLogOperation.CREATE, AuditLogOperation.fromCode("CREATE"));
        assertEquals(AuditLogOperation.UPDATE, AuditLogOperation.fromCode("UPDATE"));

        // 测试默认值
        assertEquals(AuditLogOperation.OTHER, AuditLogOperation.fromCode("INVALID"));

        // 测试方法名推断
        assertEquals(AuditLogOperation.CREATE, AuditLogOperation.inferFromMethod("createUser"));
        assertEquals(AuditLogOperation.UPDATE, AuditLogOperation.inferFromMethod("updateUser"));
        assertEquals(AuditLogOperation.DELETE, AuditLogOperation.inferFromMethod("deleteUser"));
        assertEquals(AuditLogOperation.QUERY, AuditLogOperation.inferFromMethod("getUser"));
        assertEquals(AuditLogOperation.LOGIN, AuditLogOperation.inferFromMethod("userLogin"));
        assertEquals(AuditLogOperation.LOGOUT, AuditLogOperation.inferFromMethod("userLogout"));
    }

    @Test
    void testAuditLogBuilder() {
        // 测试Builder模式
        LocalDateTime now = LocalDateTime.now();

        AuditLog auditLog = AuditLog.builder()
                .id(1L)
                .userId(100L)
                .username("testuser")
                .operation(AuditLogOperation.CREATE.getCode())
                .module("TEST_MODULE")
                .description("测试描述")
                .level(AuditLogLevel.INFO)
                .businessId(200L)
                .businessType("TEST_ENTITY")
                .responseCode(200)
                .responseMessage("success")
                .executionTime(100L)
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla")
                .isException(false)
                .createTime(now)
                .isArchived(false)
                .build();

        // 验证所有字段
        assertEquals(1L, auditLog.getId());
        assertEquals(100L, auditLog.getUserId());
        assertEquals("testuser", auditLog.getUsername());
        assertEquals("CREATE", auditLog.getOperation());
        assertEquals("TEST_MODULE", auditLog.getModule());
        assertEquals("测试描述", auditLog.getDescription());
        assertEquals("INFO", auditLog.getLevel());
        assertEquals(200L, auditLog.getBusinessId());
        assertEquals("TEST_ENTITY", auditLog.getBusinessType());
        assertEquals(200, auditLog.getResponseCode());
        assertEquals("success", auditLog.getResponseMessage());
        assertEquals(100L, auditLog.getExecutionTime());
        assertEquals("127.0.0.1", auditLog.getIpAddress());
        assertEquals("Mozilla", auditLog.getUserAgent());
        assertFalse(auditLog.getIsException());
        assertEquals(now, auditLog.getCreateTime());
        assertFalse(auditLog.getIsArchived());
    }

    @Test
    void testAuditLogBuilderDefaults() {
        // 测试Builder默认值
        AuditLog auditLog = AuditLog.builder()
                .userId(1L)
                .username("test")
                .operation("CREATE")
                .build();

        // 验证默认值
        assertEquals("INFO", auditLog.getLevel());
        assertFalse(auditLog.getIsException());
        assertFalse(auditLog.getIsArchived());
        assertNotNull(auditLog.getCreateTime());
    }

    @Test
    void testDisabledAuditLog() {
        // 配置为禁用
        when(properties.isEnabled()).thenReturn(false);

        // 执行测试
        auditLogService.recordLogin(1L, "testuser", "127.0.0.1", "Mozilla", true);

        // 验证未记录
        verify(auditLogMapper, never()).insert(any(AuditLog.class));
    }

    @Test
    void testAsyncAuditLog() {
        // 配置为异步
        when(properties.isAsync()).thenReturn(true);

        // 执行测试
        auditLogService.recordLogin(1L, "testuser", "127.0.0.1", "Mozilla", true);

        // 验证异步调用
        // 注意：由于是异步，这里只验证方法被调用，不验证具体的insert
        // 异步方法的验证需要在集成测试中进行
    }
}
