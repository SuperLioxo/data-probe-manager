-- Agent日志表 (PostgreSQL)

CREATE TABLE IF NOT EXISTS agent_log (
    id BIGSERIAL PRIMARY KEY,
    agent_code VARCHAR(100),
    level VARCHAR(20),
    logger VARCHAR(255),
    message TEXT,
    exception_stack TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_log_agent ON agent_log(agent_code);
CREATE INDEX IF NOT EXISTS idx_agent_log_time ON agent_log(timestamp);
