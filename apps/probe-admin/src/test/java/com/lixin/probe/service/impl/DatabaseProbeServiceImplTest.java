package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.entity.DatabaseProbe;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.mapper.DatabaseProbeMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.websocket.MetaProbeWebSocketHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseProbeServiceImplTest {

    @Mock
    private DatabaseProbeMapper databaseProbeMapper;

    @Mock
    private ProbeMapper probeMapper;

    @Mock
    private MetaProbeWebSocketHandler webSocketHandler;

    @InjectMocks
    private DatabaseProbeServiceImpl databaseProbeService;

    private DatabaseProbe buildTestProbe() {
        return DatabaseProbe.builder()
                .id(1L)
                .probeKey("test-db-probe")
                .name("测试数据库探针")
                .type("DATABASE")
                .status("online")
                .databaseType("PostgreSQL")
                .databaseHost("localhost")
                .databasePort(5432)
                .databaseName("test_db")
                .username("test_user")
                .password("test_pass")
                .collectInterval(60)
                .build();
    }

    @Test
    @DisplayName("list应返回所有探针")
    void testList_shouldReturnAll() {
        when(databaseProbeMapper.selectList(isNull())).thenReturn(List.of(buildTestProbe()));

        List<DatabaseProbe> result = databaseProbeService.list();
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getById应返回对应探针")
    void testGetById_shouldReturnProbe() {
        DatabaseProbe probe = buildTestProbe();
        when(databaseProbeMapper.selectById(1L)).thenReturn(probe);

        DatabaseProbe result = databaseProbeService.getById(1L);
        assertNotNull(result);
        assertEquals("test-db-probe", result.getProbeKey());
    }

    @Test
    @DisplayName("getById探针不存在应返回null")
    void testGetById_notFound_shouldReturnNull() {
        when(databaseProbeMapper.selectById(999L)).thenReturn(null);
        assertNull(databaseProbeService.getById(999L));
    }

    @Test
    @DisplayName("getByProbeKey应返回对应探针")
    void testGetByProbeKey_shouldReturnProbe() {
        DatabaseProbe probe = buildTestProbe();
        when(databaseProbeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(probe);

        DatabaseProbe result = databaseProbeService.getByProbeKey("test-db-probe");
        assertNotNull(result);
        assertEquals("test-db-probe", result.getProbeKey());
    }

    @Test
    @DisplayName("创建时连接测试失败应抛异常")
    void testCreate_connectionFailed_shouldThrow() {
        DatabaseProbe probe = DatabaseProbe.builder()
                .probeKey("bad-probe").name("bad").type("DATABASE")
                .databaseType("MySQL").databaseHost("invalid-host")
                .databasePort(3306).databaseName("test")
                .username("user").password("pass").build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> databaseProbeService.create(probe));
        assertTrue(ex.getMessage().contains("连接测试失败"));
    }

    @Test
    @DisplayName("更新探针时明文密码应被加密")
    void testUpdate_plaintextPassword_shouldEncrypt() {
        DatabaseProbe probe = buildTestProbe();
        probe.setPassword("newPassword123");

        when(databaseProbeMapper.updateById(any(DatabaseProbe.class))).thenReturn(1);

        databaseProbeService.update(probe);

        ArgumentCaptor<DatabaseProbe> captor = ArgumentCaptor.forClass(DatabaseProbe.class);
        verify(databaseProbeMapper).updateById(captor.capture());
        DatabaseProbe updated = captor.getValue();
        assertNotEquals("newPassword123", updated.getPassword(), "Password should be encrypted");
        assertTrue(updated.getPassword().contains(":"), "Encrypted password should contain ':' separator");
        assertNotNull(updated.getUpdateTime());
    }

    @Test
    @DisplayName("更新探针时已加密密码不应重新加密")
    void testUpdate_encryptedPassword_shouldNotReEncrypt() {
        DatabaseProbe probe = buildTestProbe();
        String encryptedPw = "abc123:def456";
        probe.setPassword(encryptedPw);

        when(databaseProbeMapper.updateById(any(DatabaseProbe.class))).thenReturn(1);

        databaseProbeService.update(probe);

        ArgumentCaptor<DatabaseProbe> captor = ArgumentCaptor.forClass(DatabaseProbe.class);
        verify(databaseProbeMapper).updateById(captor.capture());
        assertEquals(encryptedPw, captor.getValue().getPassword(), "Encrypted password should stay the same");
    }

    @Test
    @DisplayName("删除探针应同时删除probe表和database_probe表记录")
    void testDelete_shouldDeleteFromBothTables() {
        DatabaseProbe dbProbe = buildTestProbe();
        when(databaseProbeMapper.selectById(1L)).thenReturn(dbProbe);
        when(databaseProbeMapper.deleteById(1L)).thenReturn(1);

        Probe probeRecord = Probe.builder().id(10L).probeKey("test-db-probe").build();
        when(probeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(probeRecord);
        when(probeMapper.deleteById(10L)).thenReturn(1);

        databaseProbeService.delete(1L);

        verify(databaseProbeMapper).deleteById(1L);
        verify(probeMapper).deleteById(10L);
    }

    @Test
    @DisplayName("删除不存在的探针应抛异常")
    void testDelete_nonExistent_shouldThrow() {
        when(databaseProbeMapper.selectById(999L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> databaseProbeService.delete(999L));
    }

    @Test
    @DisplayName("更新心跳应设置在线状态和时间")
    void testUpdateHeartbeat_shouldSetOnlineAndTime() {
        DatabaseProbe probe = buildTestProbe();
        probe.setStatus("offline");
        when(databaseProbeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(probe);
        when(databaseProbeMapper.updateById(any(DatabaseProbe.class))).thenReturn(1);

        databaseProbeService.updateHeartbeat("test-db-probe");

        ArgumentCaptor<DatabaseProbe> captor = ArgumentCaptor.forClass(DatabaseProbe.class);
        verify(databaseProbeMapper).updateById(captor.capture());
        DatabaseProbe updated = captor.getValue();
        assertEquals("online", updated.getStatus());
        assertNotNull(updated.getLastHeartbeat());
        assertNotNull(updated.getUpdateTime());
    }

    @Test
    @DisplayName("更新心跳探针不存在应不操作")
    void testUpdateHeartbeat_probeNotFound_shouldDoNothing() {
        when(databaseProbeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        databaseProbeService.updateHeartbeat("nonexistent");

        verify(databaseProbeMapper, never()).updateById(any(DatabaseProbe.class));
    }

    @Test
    @DisplayName("更新采集时间应设置lastCollectTime")
    void testUpdateCollectTime_shouldSetCollectTime() {
        DatabaseProbe probe = buildTestProbe();
        when(databaseProbeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(probe);
        when(databaseProbeMapper.updateById(any(DatabaseProbe.class))).thenReturn(1);

        databaseProbeService.updateCollectTime("test-db-probe");

        ArgumentCaptor<DatabaseProbe> captor = ArgumentCaptor.forClass(DatabaseProbe.class);
        verify(databaseProbeMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getLastCollectTime());
    }

    @Test
    @DisplayName("getByDatabaseTypeAndHost应返回匹配探针")
    void testGetByDatabaseTypeAndHost_shouldReturnMatching() {
        when(databaseProbeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildTestProbe()));

        List<DatabaseProbe> result = databaseProbeService.getByDatabaseTypeAndHost("PostgreSQL", "localhost");
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("密码加密后应包含冒号分隔符")
    void testEncryptPassword_shouldContainColon() {
        DatabaseProbe probe = DatabaseProbe.builder().password("myPassword").build();
        databaseProbeService.encryptPassword(probe);
        assertTrue(probe.getPassword().contains(":"), "Encrypted password should contain ':'");
        assertNotEquals("myPassword", probe.getPassword());
    }

    @Test
    @DisplayName("已加密密码不应重新加密")
    void testEncryptPassword_alreadyEncrypted_shouldNotReEncrypt() {
        String encryptedPw = "abc123:def456";
        DatabaseProbe probe = DatabaseProbe.builder().password(encryptedPw).build();
        databaseProbeService.encryptPassword(probe);
        assertEquals(encryptedPw, probe.getPassword(), "Should not re-encrypt");
    }

    @Test
    @DisplayName("空密码不加密")
    void testEncryptPassword_nullPassword_shouldDoNothing() {
        DatabaseProbe probe = DatabaseProbe.builder().password(null).build();
        databaseProbeService.encryptPassword(probe);
        assertNull(probe.getPassword());
    }

    @Test
    @DisplayName("解密短密码应直接返回（可能为明文）")
    void testDecryptPassword_shortPassword_shouldReturnAsIs() {
        String result = databaseProbeService.decryptPassword("short");
        assertEquals("short", result);
    }

    @Test
    @DisplayName("解密空密码应返回空字符串")
    void testDecryptPassword_emptyPassword_shouldReturnEmpty() {
        assertEquals("", databaseProbeService.decryptPassword(""));
        assertEquals("", databaseProbeService.decryptPassword(null));
    }
}