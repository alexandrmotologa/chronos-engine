package com.engine.chronos.infrastructure.adapter.out.lease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chronos_partition_leases")
public class PartitionLeaseJpaEntity {

    @Id
    @Column(name = "partition_bucket", nullable = false)
    private Integer partitionBucket;

    @Column(name = "node_owner", nullable = false, length = 128)
    private String nodeOwner;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public PartitionLeaseJpaEntity() {}

    public PartitionLeaseJpaEntity(Integer partitionBucket, String nodeOwner, Instant acquiredAt, Instant expiresAt) {
        this.partitionBucket = partitionBucket;
        this.nodeOwner = nodeOwner;
        this.acquiredAt = acquiredAt;
        this.expiresAt = expiresAt;
    }

    public Integer getPartitionBucket() { return partitionBucket; }
    public String getNodeOwner() { return nodeOwner; }
    public Instant getAcquiredAt() { return acquiredAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setNodeOwner(String nodeOwner) { this.nodeOwner = nodeOwner; }
    public void setAcquiredAt(Instant acquiredAt) { this.acquiredAt = acquiredAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
