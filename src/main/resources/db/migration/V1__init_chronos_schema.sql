-- Chronos Engine Schema Initialization

CREATE TABLE IF NOT EXISTS chronos_tasks (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) UNIQUE,
    partition_bucket INT NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    target TEXT NOT NULL,
    headers TEXT,
    payload TEXT NOT NULL,
    scheduled_time_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    cron_expression VARCHAR(255),
    max_attempts INT NOT NULL,
    initial_interval_ms BIGINT NOT NULL,
    multiplier DOUBLE PRECISION NOT NULL,
    jitter_factor DOUBLE PRECISION NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Index for fetching due tasks within partitions
CREATE INDEX IF NOT EXISTS idx_chronos_tasks_part_status_sched 
ON chronos_tasks (partition_bucket, status, scheduled_time_utc);

-- Index for global due task lookups
CREATE INDEX IF NOT EXISTS idx_chronos_tasks_status_sched 
ON chronos_tasks (status, scheduled_time_utc);

-- Index for orphan harvesting
CREATE INDEX IF NOT EXISTS idx_chronos_tasks_lease_exp 
ON chronos_tasks (status, lease_expires_at) 
WHERE status IN ('ACQUIRED', 'EXECUTING');

CREATE TABLE IF NOT EXISTS chronos_executions (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES chronos_tasks(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    duration_ms INT NOT NULL,
    status_code INT,
    response_body TEXT,
    error_message TEXT,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chronos_executions_task_id 
ON chronos_executions (task_id, executed_at DESC);

CREATE TABLE IF NOT EXISTS chronos_nodes (
    node_id VARCHAR(128) PRIMARY KEY,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL
);
