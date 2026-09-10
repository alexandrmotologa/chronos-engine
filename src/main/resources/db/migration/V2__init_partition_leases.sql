-- Chronos Engine Partition Leases Schema

CREATE TABLE IF NOT EXISTS chronos_partition_leases (
    partition_bucket INT PRIMARY KEY,
    node_owner VARCHAR(128) NOT NULL,
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chronos_part_leases_owner_exp 
ON chronos_partition_leases (node_owner, expires_at);
