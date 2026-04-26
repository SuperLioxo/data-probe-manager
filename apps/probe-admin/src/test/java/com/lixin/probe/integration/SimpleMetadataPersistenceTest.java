package com.lixin.probe.integration;

import com.lixin.probe.mapper.ColumnInfoMapper;
import com.lixin.probe.mapper.DatabaseMetadataMapper;
import com.lixin.probe.mapper.TableInfoMapper;
import com.lixin.probe.entity.ColumnInfo;
import com.lixin.probe.entity.DatabaseMetadata;
import com.lixin.probe.entity.TableInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 简化的元数据持久化测试
 * 直接测试数据库插入，不依赖复杂的业务逻辑
 */
@SpringBootTest
@ActiveProfiles("test")
public class SimpleMetadataPersistenceTest {

    @Autowired
    private DatabaseMetadataMapper databaseMetadataMapper;

    @Autowired
    private TableInfoMapper tableInfoMapper;

    @Autowired
    private ColumnInfoMapper columnInfoMapper;

    private String testProbeKey;

    @BeforeEach
    void setUp() {
        testProbeKey = "test-probe-" + System.currentTimeMillis();
    }

    @Test
    @Transactional
    void testSaveDatabaseMetadata() {
        // 1. 直接插入database_metadata记录
        DatabaseMetadata metadata = DatabaseMetadata.builder()
            .probeKey(testProbeKey)
            .databaseType("postgresql")
            .databaseName("test_db")
            .version("16")
            .charset("UTF8")
            .collation("en_US.UTF-8")
            .url("jdbc:postgresql://localhost:5432/test_db")
            .createTime(LocalDateTime.now())
            .updateTime(LocalDateTime.now())
            .build();

        int result = databaseMetadataMapper.insert(metadata);
        assertThat(result).isEqualTo(1);
        assertThat(metadata.getId()).isNotNull();

        System.out.println("✅ database_metadata 插入成功");
        System.out.println("   ID: " + metadata.getId());
        System.out.println("   数据库: " + metadata.getDatabaseName());

        // 2. 插入table_info记录
        TableInfo table1 = TableInfo.builder()
            .probeKey(testProbeKey)
            .tableName("users")
            .engine(null)
            .rowCount(100L)
            .dataSize(16384L)
            .indexSize(8192L)
            .totalSize(24576L)
            .createTimeStr("2024-01-01 00:00:00")
            .updateTimeStr("2024-03-22 12:00:00")
            .createTime(LocalDateTime.now())
            .updateTime(LocalDateTime.now())
            .build();

        int tableResult = tableInfoMapper.insert(table1);
        assertThat(tableResult).isEqualTo(1);
        assertThat(table1.getId()).isNotNull();

        System.out.println("✅ table_info 插入成功");
        System.out.println("   表名: " + table1.getTableName());

        // 3. 插入column_info记录
        ColumnInfo column1 = ColumnInfo.builder()
            .probeKey(testProbeKey)
            .tableName("users")
            .columnName("id")
            .dataType("int4")
            .columnType("INTEGER")
            .isNullable(false)
            .keyType("PRI")
            .defaultValue(null)
            .extra("auto_increment")
            .comment("主键ID")
            .createTime(LocalDateTime.now())
            .build();

        ColumnInfo column2 = ColumnInfo.builder()
            .probeKey(testProbeKey)
            .tableName("users")
            .columnName("username")
            .dataType("varchar")
            .columnType("VARCHAR(50)")
            .isNullable(false)
            .keyType("UNI")
            .defaultValue(null)
            .extra(null)
            .comment("用户名")
            .createTime(LocalDateTime.now())
            .build();

        int col1Result = columnInfoMapper.insert(column1);
        int col2Result = columnInfoMapper.insert(column2);

        assertThat(col1Result).isEqualTo(1);
        assertThat(col2Result).isEqualTo(1);

        System.out.println("✅ column_info 插入成功");
        System.out.println("   列1: " + column1.getColumnName() + " " + column1.getColumnType());
        System.out.println("   列2: " + column2.getColumnName() + " " + column2.getColumnType());

        System.out.println("\n🎉 简化元数据持久化测试通过!");
        System.out.println("   所有3个表都成功插入了数据");
    }
}
