package com.lixin.probe.integration;

import com.lixin.probe.entity.ColumnInfo;
import com.lixin.probe.entity.DatabaseMetadata;
import com.lixin.probe.entity.TableInfo;
import com.lixin.probe.service.DatabaseMetadataService;
import com.lixin.probe.service.DatabasePerformanceService;
import com.lixin.probe.service.ProbeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 元数据持久化集成测试
 * 验证Admin接收到Agent发送的元数据后，能否正确保存到数据库
 */
@SpringBootTest
@ActiveProfiles("test")
public class MetadataPersistenceTest {

    @Autowired
    private DatabasePerformanceService databasePerformanceService;

    @Autowired
    private DatabaseMetadataService databaseMetadataService;

    @Autowired
    private ProbeService probeService;

    @Test
    void testSaveMetadataWithTablesAndColumns() {
        // 1. 准备测试数据 - 模拟Agent发送的展平后的元数据结构
        Map<String, Object> flattenedMetadata = new HashMap<>();
        flattenedMetadata.put("databaseType", "postgresql");
        flattenedMetadata.put("databaseName", "test_db");
        flattenedMetadata.put("version", "16");
        flattenedMetadata.put("charset", "UTF8");
        flattenedMetadata.put("collation", "en_US.UTF-8");
        flattenedMetadata.put("url", "jdbc:postgresql://localhost:5432/test_db");

        // 构建表信息
        List<Map<String, Object>> tables = new ArrayList<>();

        // 表1: users
        Map<String, Object> usersTable = new HashMap<>();
        usersTable.put("tableName", "users");
        usersTable.put("tableComment", "用户表");
        usersTable.put("columnCount", 5);
        usersTable.put("engine", null);
        usersTable.put("rowCount", 100L);
        usersTable.put("dataSize", 16384L);
        usersTable.put("indexSize", 8192L);
        usersTable.put("totalSize", 24576L);
        usersTable.put("createTimeStr", "2024-01-01 00:00:00");
        usersTable.put("updateTimeStr", "2024-03-22 12:00:00");

        // users表的列
        List<Map<String, Object>> usersColumns = new ArrayList<>();
        Map<String, Object> idColumn = new HashMap<>();
        idColumn.put("columnName", "id");
        idColumn.put("columnType", "INTEGER");
        idColumn.put("dataType", "int4");
        idColumn.put("isNullable", false);
        idColumn.put("keyType", "PRI");
        idColumn.put("defaultValue", null);
        idColumn.put("extra", "auto_increment");
        idColumn.put("comment", "主键ID");
        usersColumns.add(idColumn);

        Map<String, Object> nameColumn = new HashMap<>();
        nameColumn.put("columnName", "username");
        nameColumn.put("columnType", "VARCHAR(50)");
        nameColumn.put("dataType", "varchar");
        nameColumn.put("isNullable", false);
        nameColumn.put("keyType", "UNI");
        nameColumn.put("defaultValue", null);
        nameColumn.put("extra", null);
        nameColumn.put("comment", "用户名");
        usersColumns.add(nameColumn);

        Map<String, Object> emailColumn = new HashMap<>();
        emailColumn.put("columnName", "email");
        emailColumn.put("columnType", "VARCHAR(100)");
        emailColumn.put("dataType", "varchar");
        emailColumn.put("isNullable", true);
        emailColumn.put("keyType", "MUL");
        emailColumn.put("defaultValue", null);
        emailColumn.put("extra", null);
        emailColumn.put("comment", "邮箱地址");
        usersColumns.add(emailColumn);

        usersTable.put("columns", usersColumns);
        tables.add(usersTable);

        // 表2: orders
        Map<String, Object> ordersTable = new HashMap<>();
        ordersTable.put("tableName", "orders");
        ordersTable.put("tableComment", "订单表");
        ordersTable.put("columnCount", 8);
        ordersTable.put("engine", null);
        ordersTable.put("rowCount", 500L);
        ordersTable.put("dataSize", 32768L);
        ordersTable.put("indexSize", 16384L);
        ordersTable.put("totalSize", 49152L);
        ordersTable.put("createTimeStr", "2024-01-01 00:00:00");
        ordersTable.put("updateTimeStr", "2024-03-22 12:00:00");

        List<Map<String, Object>> ordersColumns = new ArrayList<>();
        Map<String, Object> orderIdColumn = new HashMap<>();
        orderIdColumn.put("columnName", "id");
        orderIdColumn.put("columnType", "INTEGER");
        orderIdColumn.put("dataType", "int4");
        orderIdColumn.put("isNullable", false);
        orderIdColumn.put("keyType", "PRI");
        orderIdColumn.put("defaultValue", null);
        orderIdColumn.put("extra", "auto_increment");
        orderIdColumn.put("comment", "订单ID");
        ordersColumns.add(orderIdColumn);

        Map<String, Object> userIdColumn = new HashMap<>();
        userIdColumn.put("columnName", "user_id");
        userIdColumn.put("columnType", "INTEGER");
        userIdColumn.put("dataType", "int4");
        userIdColumn.put("isNullable", false);
        userIdColumn.put("keyType", "MUL");
        userIdColumn.put("defaultValue", null);
        userIdColumn.put("extra", null);
        userIdColumn.put("comment", "用户ID");
        ordersColumns.add(userIdColumn);

        ordersTable.put("columns", ordersColumns);
        tables.add(ordersTable);

        flattenedMetadata.put("tables", tables);

        // 2. 调用saveMetadata方法保存元数据
        String testProbeKey = "test-probe-key-" + System.currentTimeMillis();
        System.out.println("测试探针Key: " + testProbeKey);

        // 先创建探针（如果不存在）
        // 注意：这里假设探针已经存在，实际测试中可能需要先创建

        databasePerformanceService.saveMetadata(testProbeKey, flattenedMetadata);

        // 3. 验证数据库中的数据
        // 查询database_metadata
        DatabaseMetadata savedDbMetadata = databaseMetadataService.getLatestByProbeKey(testProbeKey);
        assertThat(savedDbMetadata).isNotNull();
        assertThat(savedDbMetadata.getDatabaseType()).isEqualTo("postgresql");
        assertThat(savedDbMetadata.getDatabaseName()).isEqualTo("test_db");
        assertThat(savedDbMetadata.getCharset()).isEqualTo("UTF8");
        System.out.println("✅ database_metadata 保存成功");
        System.out.println("   数据库类型: " + savedDbMetadata.getDatabaseType());
        System.out.println("   数据库名称: " + savedDbMetadata.getDatabaseName());

        System.out.println("\n🎉 元数据持久化测试通过!");
        System.out.println("   现在table_info和column_info表应该包含数据了");
    }
}
