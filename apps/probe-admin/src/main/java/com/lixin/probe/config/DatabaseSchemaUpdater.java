package com.lixin.probe.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库Schema更新器 - 在应用启动时自动添加缺失的列
 */
@Component
public class DatabaseSchemaUpdater {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseSchemaUpdater.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void updateSchema() {
        try {
            // 检查 acknowledged_at 列是否存在
            Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'alert' AND column_name = 'acknowledged_at'",
                Integer.class
            );

            if (columnCount != null && columnCount == 0) {
                log.info("Adding missing acknowledged_at column to alert table...");
                jdbcTemplate.execute("ALTER TABLE alert ADD COLUMN acknowledged_at TIMESTAMP");
                log.info("✓ Added acknowledged_at column successfully");
            } else {
                log.info("acknowledged_at column already exists");
            }

        } catch (Exception e) {
            log.error("Failed to update database schema", e);
            // 不抛出异常，允许应用继续启动
        }

        createTableIfNotExists("agent_log",
                "CREATE TABLE IF NOT EXISTS agent_log (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "agent_code VARCHAR(64) NOT NULL, " +
                "level VARCHAR(16), " +
                "logger VARCHAR(256), " +
                "message TEXT, " +
                "exception_stack TEXT, " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        createTableIfNotExists("dead_letter_task",
                "CREATE TABLE IF NOT EXISTS dead_letter_task (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "original_task_id BIGINT, " +
                "task_name VARCHAR(128), " +
                "source_probe_key VARCHAR(128), " +
                "source_table_name VARCHAR(128), " +
                "target_type VARCHAR(32), " +
                "target_config TEXT, " +
                "sync_mode VARCHAR(32), " +
                "failure_reason TEXT, " +
                "failure_stack TEXT, " +
                "retry_count INT DEFAULT 0, " +
                "max_retries INT DEFAULT 3, " +
                "status VARCHAR(32) DEFAULT 'PENDING', " +
                "next_retry_time TIMESTAMP, " +
                "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_retry_time TIMESTAMP)");

        createTableIfNotExists("alert_channel",
                "CREATE TABLE IF NOT EXISTS alert_channel (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "name VARCHAR(128) NOT NULL, " +
                "channel_type VARCHAR(32) NOT NULL, " +
                "config TEXT, " +
                "enabled BOOLEAN DEFAULT TRUE, " +
                "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        createTableIfNotExists("agent_version",
                "CREATE TABLE IF NOT EXISTS agent_version (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "version VARCHAR(64) NOT NULL, " +
                "file_path VARCHAR(512), " +
                "file_size BIGINT, " +
                "checksum VARCHAR(128), " +
                "release_notes TEXT, " +
                "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "uploaded_by VARCHAR(64))");
    }

    private void createTableIfNotExists(String tableName, String ddl) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + tableName + "'",
                    Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(ddl);
                log.info("Created table: {}", tableName);
            }
        } catch (Exception e) {
            log.warn("Failed to create table {}: {}", tableName, e.getMessage());
        }
    }
}
