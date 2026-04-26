-- 告警渠道表 (PostgreSQL)

CREATE TABLE IF NOT EXISTS alert_channel (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    channel_type VARCHAR(30) NOT NULL,
    config TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_channel_type ON alert_channel(channel_type);
