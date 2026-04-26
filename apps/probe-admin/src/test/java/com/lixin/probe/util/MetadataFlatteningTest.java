package com.lixin.probe.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 元数据展平测试
 * 验证Agent发送的Metadata结构能否被正确转换为Admin期望的格式
 */
public class MetadataFlatteningTest {

    @Test
    void testFlattenMetadataStructure() {
        // 1. 模拟Agent发送的Metadata结构
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "postgresql");
        metadata.put("version", "16");

        // 构建databases map
        Map<String, Object> databases = new HashMap<>();

        // 构建database对象
        Map<String, Object> database = new HashMap<>();
        database.put("type", "postgresql");
        database.put("name", "test_db");
        database.put("version", "16");
        database.put("charset", "UTF8");
        database.put("collation", "en_US.UTF-8");
        database.put("host", "localhost");
        database.put("port", 5432);
        database.put("tableCount", 3);
        database.put("columnCount", 15);

        // 构建tables map
        Map<String, Object> tables = new HashMap<>();

        // 表1: users
        Map<String, Object> usersTable = new HashMap<>();
        usersTable.put("name", "users");
        usersTable.put("comment", "用户表");
        usersTable.put("columnCount", 5);

        Map<String, Object> usersColumns = new HashMap<>();
        Map<String, Object> idColumn = new HashMap<>();
        idColumn.put("name", "id");
        idColumn.put("type", "INTEGER");
        idColumn.put("comment", "主键");
        usersColumns.put("id", idColumn);

        Map<String, Object> nameColumn = new HashMap<>();
        nameColumn.put("name", "username");
        nameColumn.put("type", "VARCHAR(50)");
        nameColumn.put("comment", "用户名");
        usersColumns.put("username", nameColumn);

        usersTable.put("columns", usersColumns);
        tables.put("users", usersTable);

        // 表2: orders
        Map<String, Object> ordersTable = new HashMap<>();
        ordersTable.put("name", "orders");
        ordersTable.put("comment", "订单表");
        ordersTable.put("columnCount", 4);

        Map<String, Object> ordersColumns = new HashMap<>();
        Map<String, Object> orderIdColumn = new HashMap<>();
        orderIdColumn.put("name", "id");
        orderIdColumn.put("type", "INTEGER");
        ordersColumns.put("id", orderIdColumn);
        ordersTable.put("columns", ordersColumns);
        tables.put("orders", ordersTable);

        database.put("tables", tables);
        databases.put("test_db", database);
        metadata.put("databases", databases);

        // 2. 执行展平操作（模拟ProbePushMessageHandler.flattenMetadata的逻辑）
        Map<String, Object> flattened = new HashMap<>();

        Object databasesObj = metadata.get("databases");
        if (databasesObj instanceof Map) {
            Map<String, Object> dbMap = (Map<String, Object>) databasesObj;

            if (!dbMap.isEmpty()) {
                Map.Entry<String, Object> firstEntry = dbMap.entrySet().iterator().next();
                Map<String, Object> db = (Map<String, Object>) firstEntry.getValue();

                // 提取数据库基本信息
                flattened.put("databaseType", db.get("type"));
                flattened.put("databaseName", db.get("name"));
                flattened.put("version", db.get("version"));
                flattened.put("charset", db.get("charset"));
                flattened.put("collation", db.get("collation"));

                // 构建URL
                String url = String.format("jdbc:%s://%s:%s/%s",
                    db.get("type"), db.get("host"), db.get("port"), db.get("name"));
                flattened.put("url", url);

                // 提取表信息
                Object tablesObj = db.get("tables");
                if (tablesObj instanceof Map) {
                    Map<String, Object> tablesMap = (Map<String, Object>) tablesObj;
                    List<Map<String, Object>> tableList = new ArrayList<>();

                    for (Map.Entry<String, Object> entry : tablesMap.entrySet()) {
                        Map<String, Object> table = (Map<String, Object>) entry.getValue();
                        Map<String, Object> tableData = new HashMap<>();
                        tableData.put("tableName", table.get("name"));
                        tableData.put("tableComment", table.get("comment"));
                        tableData.put("columnCount", table.get("columnCount"));

                        // 提取列信息
                        Object columnsObj = table.get("columns");
                        if (columnsObj instanceof Map) {
                            Map<String, Object> columnsMap = (Map<String, Object>) columnsObj;
                            List<Map<String, Object>> columnList = new ArrayList<>();

                            for (Map.Entry<String, Object> colEntry : columnsMap.entrySet()) {
                                Map<String, Object> column = (Map<String, Object>) colEntry.getValue();
                                Map<String, Object> columnData = new HashMap<>();
                                columnData.put("columnName", column.get("name"));
                                columnData.put("columnType", column.get("type"));
                                columnData.put("columnComment", column.get("comment"));
                                columnList.add(columnData);
                            }
                            tableData.put("columns", columnList);
                        }

                        tableList.add(tableData);
                    }
                    flattened.put("tables", tableList);
                    System.out.println("✅ 提取到 " + tableList.size() + " 个表信息");
                }
            }
        }

        // 3. 验证结果
        assertThat(flattened.get("databaseType")).isEqualTo("postgresql");
        assertThat(flattened.get("databaseName")).isEqualTo("test_db");
        assertThat(flattened.get("charset")).isEqualTo("UTF8");
        assertThat(flattened.get("url")).isEqualTo("jdbc:postgresql://localhost:5432/test_db");

        // 关键验证：tables字段是否存在且不为空
        assertThat(flattened.containsKey("tables")).isTrue();
        List<Map<String, Object>> tablesList = (List<Map<String, Object>>) flattened.get("tables");
        assertThat(tablesList).isNotNull();
        assertThat(tablesList).hasSize(2);

        // 验证表信息（不假设顺序）
        Optional<Map<String, Object>> usersTableOpt = tablesList.stream()
            .filter(t -> "users".equals(t.get("tableName")))
            .findFirst();
        assertThat(usersTableOpt).isPresent();
        Map<String, Object> usersTableData = usersTableOpt.get();
        assertThat(usersTableData.get("tableComment")).isEqualTo("用户表");
        assertThat(usersTableData.get("columnCount")).isEqualTo(5);

        List<Map<String, Object>> usersColumnsList = (List<Map<String, Object>>) usersTableData.get("columns");
        assertThat(usersColumnsList).hasSize(2);
        assertThat(usersColumnsList).anyMatch(c -> "id".equals(c.get("columnName")));
        assertThat(usersColumnsList).anyMatch(c -> "username".equals(c.get("columnName")));

        // 验证第二个表
        Optional<Map<String, Object>> ordersTableOpt = tablesList.stream()
            .filter(t -> "orders".equals(t.get("tableName")))
            .findFirst();
        assertThat(ordersTableOpt).isPresent();
        Map<String, Object> ordersTableData = ordersTableOpt.get();
        assertThat(ordersTableData.get("columnCount")).isEqualTo(4);

        System.out.println("✅ 元数据展平测试通过!");
        System.out.println("   数据库: " + flattened.get("databaseName"));
        System.out.println("   表数量: " + tablesList.size());
        System.out.println("   列数量: " + usersColumnsList.size());
    }
}
