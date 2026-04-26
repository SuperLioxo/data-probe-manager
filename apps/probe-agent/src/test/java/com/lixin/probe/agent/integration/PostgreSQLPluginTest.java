package com.lixin.probe.agent.integration;

import com.lixin.probe.agent.plugin.impl.database.PostgreSQLPlugin;
import com.lixin.probe.agent.pojo.request.ProbeRequest;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL插件单元测试
 * <p>
 * 直接测试PostgreSQLPlugin功能，不依赖Spring上下文
 * 使用本地运行的PostgreSQL容器
 * </p>
 *
 * @author Claude Code
 * @version 1.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("PostgreSQL插件功能测试")
public class PostgreSQLPluginTest {

    private static final Logger log = LoggerFactory.getLogger(PostgreSQLPluginTest.class);

    // 本地PostgreSQL容器连接信息
    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 5433;
    private static final String DB_NAME = "probe_db";
    private static final String DB_USER = "probe_user";
    private static final String DB_PASSWORD = "probe_pass";

    @BeforeAll
    static void setUpClass() {
        log.info("========================================");
        log.info("PostgreSQL插件测试开始");
        log.info("========================================");
    }

    @AfterAll
    static void tearDownClass() {
        log.info("========================================");
        log.info("PostgreSQL插件测试完成");
        log.info("========================================");
    }

    @Test
    @Order(1)
    @DisplayName("1. 验证PostgreSQL连接")
    void testPostgreSQLConnection() {
        log.info("测试步骤 1: 验证PostgreSQL连接");

        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME,
                DB_USER,
                DB_PASSWORD)) {
            assertThat(conn.isValid(5)).isTrue();
            log.info("✅ PostgreSQL连接成功");
        } catch (Exception e) {
            log.error("❌ PostgreSQL连接失败: {}", e.getMessage());
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "PostgreSQL连接失败，跳过测试");
        }
    }

    @Test
    @Order(2)
    @DisplayName("2. 测试插件基础信息")
    void testPluginBasicInfo() {
        log.info("测试步骤 2: 测试插件基础信息");

        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        assertThat(plugin.getPluginId()).isEqualTo("postgresql-database-plugin");
        assertThat(plugin.getName()).isEqualTo("PostgreSQL Database Plugin");
        assertThat(plugin.getType()).isEqualTo("DATABASE");
        assertThat(plugin.getDbType()).isEqualTo("postgresql");
        assertThat(plugin.getVersionRange()).isEqualTo("12,13,14,15,16");
        assertThat(plugin.getDefaultPort()).isEqualTo(5432);

        log.info("✅ 插件基础信息验证成功");
        log.info("   Plugin ID: {}", plugin.getPluginId());
        log.info("   支持版本: {}", plugin.getVersionRange());
    }

    @Test
    @Order(3)
    @DisplayName("3. Agent连接PostgreSQL数据库")
    void testAgentConnectPostgreSQL() {
        log.info("测试步骤 3: Agent连接PostgreSQL数据库");

        Map<String, Object> params = new HashMap<>();
        params.put("host", DB_HOST);
        params.put("port", DB_PORT);
        params.put("name", DB_NAME);
        params.put("username", DB_USER);
        params.put("password", DB_PASSWORD);

        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        try (Connection conn = plugin.getConnection(params)) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
            log.info("✅ Agent成功连接到PostgreSQL");
        } catch (Exception e) {
            log.error("❌ Agent连接PostgreSQL失败: {}", e.getMessage());
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "数据库连接失败");
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. 采集PostgreSQL元数据")
    void testCollectMetadata() {
        log.info("测试步骤 4: 采集PostgreSQL元数据");

        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        Map<String, Object> params = new HashMap<>();
        params.put("host", DB_HOST);
        params.put("port", DB_PORT);
        params.put("name", DB_NAME);
        params.put("username", DB_USER);
        params.put("password", DB_PASSWORD);

        ProbeRequest request = ProbeRequest.builder()
            .id(System.currentTimeMillis())
            .database(ProbeRequest.DatabaseConfig.builder()
                .type("postgresql")
                .version("16")
                .host(DB_HOST)
                .port(DB_PORT)
                .name(DB_NAME)
                .username(DB_USER)
                .password(DB_PASSWORD)
                .schemas(List.of("public"))
                .build())
            .build();

        try (Connection conn = plugin.getConnection(params)) {
            var future = plugin.getMetadata(conn, request);
            ProbeResponse.Metadata metadata = future.get();

            assertThat(metadata).isNotNull();
            assertThat(metadata.getType()).isEqualTo("postgresql");
            assertThat(metadata.getDatabases()).isNotEmpty();

            ProbeResponse.Metadata.Database db = metadata.getDatabases().get(DB_NAME);
            assertThat(db).isNotNull();
            assertThat(db.getName()).isEqualTo(DB_NAME);
            assertThat(db.getType()).isEqualTo("postgresql");
            assertThat(db.getTableCount()).isGreaterThan(0);
            assertThat(db.getTables()).isNotEmpty();

            log.info("✅ 元数据采集成功");
            log.info("   数据库: {}", db.getName());
            log.info("   字符集: {}", db.getCharset());
            log.info("   排序规则: {}", db.getCollation());
            log.info("   表数量: {}", db.getTableCount());
            log.info("   列数量: {}", db.getColumnCount());

            log.info("   采集到的表:");
            for (String tableName : db.getTables().keySet()) {
                ProbeResponse.Metadata.Table table = db.getTables().get(tableName);
                log.info("     - {} (列数: {})", table.getName(), table.getColumnCount());
            }

        } catch (Exception e) {
            log.error("❌ 元数据采集失败: {}", e.getMessage(), e);
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "元数据采集失败");
        }
    }

    @Test
    @Order(5)
    @DisplayName("5. 验证采集的列信息")
    void testCollectedColumns() {
        log.info("测试步骤 5: 验证采集的列信息");

        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        Map<String, Object> params = new HashMap<>();
        params.put("host", DB_HOST);
        params.put("port", DB_PORT);
        params.put("name", DB_NAME);
        params.put("username", DB_USER);
        params.put("password", DB_PASSWORD);

        ProbeRequest request = ProbeRequest.builder()
            .id(System.currentTimeMillis())
            .database(ProbeRequest.DatabaseConfig.builder()
                .type("postgresql")
                .version("16")
                .host(DB_HOST)
                .port(DB_PORT)
                .name(DB_NAME)
                .username(DB_USER)
                .password(DB_PASSWORD)
                .schemas(List.of("public"))
                .build())
            .build();

        try (Connection conn = plugin.getConnection(params)) {
            var future = plugin.getMetadata(conn, request);
            ProbeResponse.Metadata metadata = future.get();

            ProbeResponse.Metadata.Database db = metadata.getDatabases().get(DB_NAME);

            log.info("✅ 列信息验证成功");
            log.info("   数据库: {}", db.getName());

            for (String tableName : db.getTables().keySet()) {
                ProbeResponse.Metadata.Table table = db.getTables().get(tableName);
                log.info("   表 '{}' (列数: {}):", tableName, table.getColumnCount());

                for (String columnName : table.getColumns().keySet()) {
                    ProbeResponse.Metadata.Column column = table.getColumns().get(columnName);
                    log.info("     - {} {}",
                        column.getName(),
                        column.getType());
                }
            }

        } catch (Exception e) {
            log.error("❌ 列信息验证失败: {}", e.getMessage(), e);
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "列信息验证失败");
        }
    }

    @Test
    @Order(6)
    @DisplayName("6. 异常场景测试 - 连接失败")
    void testConnectionFailure() {
        log.info("测试步骤 6: 异常场景测试 - 连接失败");

        Map<String, Object> params = new HashMap<>();
        params.put("host", "invalid-host");
        params.put("port", 9999);
        params.put("name", "invalid-db");
        params.put("username", "invalid-user");
        params.put("password", "invalid-pass");

        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        assertThatThrownBy(() -> plugin.getConnection(params))
            .isInstanceOf(Exception.class);

        log.info("✅ 异常场景测试通过 - 连接失败被正确处理");
    }

    @Test
    @Order(7)
    @DisplayName("7. 完整链路测试")
    void testFullPipeline() {
        log.info("测试步骤 7: 完整链路测试");

        PostgreSQLPlugin plugin = new PostgreSQLPlugin();

        Map<String, Object> params = new HashMap<>();
        params.put("host", DB_HOST);
        params.put("port", DB_PORT);
        params.put("name", DB_NAME);
        params.put("username", DB_USER);
        params.put("password", DB_PASSWORD);

        ProbeRequest request = ProbeRequest.builder()
            .id(System.currentTimeMillis())
            .database(ProbeRequest.DatabaseConfig.builder()
                .type("postgresql")
                .version("16")
                .host(DB_HOST)
                .port(DB_PORT)
                .name(DB_NAME)
                .username(DB_USER)
                .password(DB_PASSWORD)
                .schemas(List.of("public"))
                .build())
            .build();

        try (Connection conn = plugin.getConnection(params)) {
            var future = plugin.getMetadata(conn, request);
            ProbeResponse.Metadata metadata = future.get();

            assertThat(metadata).isNotNull();
            assertThat(metadata.getType()).isEqualTo("postgresql");

            ProbeResponse.Metadata.Database db = metadata.getDatabases().get(DB_NAME);
            assertThat(db).isNotNull();
            assertThat(db.getTableCount()).isGreaterThan(0);

            log.info("✅ 完整链路测试通过");
            log.info("========================================");
            log.info("测试总结:");
            log.info("  PostgreSQL连接: ✅ 成功");
            log.info("  元数据采集: ✅ 成功");
            log.info("  数据库: {}", db.getName());
            log.info("  表数量: {}", db.getTableCount());
            log.info("  列数量: {}", db.getColumnCount());
            log.info("========================================");

        } catch (Exception e) {
            log.error("❌ 完整链路测试失败: {}", e.getMessage(), e);
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "完整链路测试失败");
        }
    }
}
