-- Webhook 数据接收相关表

CREATE TABLE IF NOT EXISTS webhook_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    webhook_name VARCHAR(200) NOT NULL,
    webhook_key VARCHAR(64) NOT NULL UNIQUE,
    schema_description TEXT,
    target_probe_key VARCHAR(255),
    target_table_name VARCHAR(255),
    enabled BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_received_time TIMESTAMP,
    receive_count BIGINT DEFAULT 0,
    INDEX idx_key (webhook_key)
);

CREATE TABLE IF NOT EXISTS webhook_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    webhook_key VARCHAR(64) NOT NULL,
    source_ip VARCHAR(50),
    payload TEXT,
    payload_checksum VARCHAR(64),
    status VARCHAR(20) DEFAULT 'RECEIVED',
    process_result TEXT,
    received_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_time TIMESTAMP,
    INDEX idx_webhook_time (webhook_key, received_time)
);
