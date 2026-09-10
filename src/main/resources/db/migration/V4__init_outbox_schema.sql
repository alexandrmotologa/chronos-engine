CREATE TABLE IF NOT EXISTS chronos_outbox (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255),
    task_type VARCHAR(32) NOT NULL,
    target TEXT NOT NULL,
    headers TEXT,
    payload TEXT,
    scheduled_time_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    cron_expression VARCHAR(120),
    max_attempts INT,
    initial_interval_ms BIGINT,
    multiplier DOUBLE PRECISION,
    jitter_factor DOUBLE PRECISION,
    tags TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_chronos_outbox_pending 
ON chronos_outbox (status, created_at);
