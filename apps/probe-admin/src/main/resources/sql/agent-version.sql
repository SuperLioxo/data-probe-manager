-- Agent版本管理表 (PostgreSQL)

CREATE TABLE IF NOT EXISTS agent_version (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    file_path VARCHAR(500),
    file_size BIGINT,
    checksum VARCHAR(128),
    release_notes TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(100)
);
