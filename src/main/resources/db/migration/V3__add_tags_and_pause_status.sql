-- Chronos Engine Tags and Multi-tenancy Support

ALTER TABLE chronos_tasks ADD COLUMN IF NOT EXISTS tags TEXT;

CREATE INDEX IF NOT EXISTS idx_chronos_tasks_tags 
ON chronos_tasks (tags);
