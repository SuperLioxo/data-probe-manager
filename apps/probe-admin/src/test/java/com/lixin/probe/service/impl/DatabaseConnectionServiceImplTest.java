package com.lixin.probe.service.impl;

import com.lixin.probe.entity.DatabaseConnection;
import com.lixin.probe.exception.BusinessException;
import com.lixin.probe.mapper.DatabaseConnectionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseConnectionServiceImplTest {

    @Mock
    private DatabaseConnectionMapper databaseConnectionMapper;

    @InjectMocks
    private DatabaseConnectionServiceImpl databaseConnectionService;

    private DatabaseConnection createTestConnection(String dbType) {
        DatabaseConnection conn = new DatabaseConnection();
        conn.setName("test-db");
        conn.setDatabaseType(dbType);
        conn.setDatabaseHost("invalid-host");
        conn.setDatabasePort(3306);
        conn.setDatabaseName("test");
        conn.setUsername("root");
        conn.setPassword("pass");
        return conn;
    }

    @Test
    @DisplayName("创建连接失败时应抛出BusinessException")
    void testCreateConnection_failed_shouldThrowBusinessException() {
        DatabaseConnection conn = createTestConnection("MySQL");
        assertThrows(BusinessException.class, () -> databaseConnectionService.createConnection(conn));
    }

    @Test
    @DisplayName("删除连接应调用deleteById")
    void testDeleteConnection_shouldCallMapper() {
        databaseConnectionService.deleteConnection(1L);
        verify(databaseConnectionMapper).deleteById(1L);
    }

    @Test
    @DisplayName("不支持的数据库类型应返回false")
    void testTestConnection_unsupportedType_shouldReturnFalse() {
        DatabaseConnection conn = createTestConnection("Cassandra");
        conn.setDatabaseHost("localhost");
        conn.setDatabasePort(9042);

        assertFalse(databaseConnectionService.testConnection(conn));
    }
}
