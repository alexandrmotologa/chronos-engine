package com.engine.chronos.infrastructure.adapter.out.persistence;

import com.engine.chronos.domain.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "chronos_tasks")
public class TaskJpaEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "partition_bucket", nullable = false)
    private int partitionBucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String target;

    @Column(columnDefinition = "TEXT")
    private String headers;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "scheduled_time_utc", nullable = false)
    private Instant scheduledTimeUtc;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "initial_interval_ms", nullable = false)
    private long initialIntervalMs;

    @Column(nullable = false)
    private double multiplier;

    @Column(name = "jitter_factor", nullable = false)
    private double jitterFactor;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TaskJpaEntity() {}

    public static TaskJpaEntity fromDomain(Task task) {
        TaskJpaEntity entity = new TaskJpaEntity();
        entity.id = task.getId().value();
        entity.idempotencyKey = task.getIdempotencyKey();
        entity.partitionBucket = task.getPartitionBucket();
        entity.type = task.getType();
        entity.status = task.getStatus();
        entity.target = task.getPayload().target();

        try {
            entity.headers = task.getPayload().headers().isEmpty()
                    ? null
                    : MAPPER.writeValueAsString(task.getPayload().headers());
        } catch (Exception e) {
            entity.headers = null;
        }

        entity.payload = task.getPayload().body();
        entity.scheduledTimeUtc = task.getScheduleRule().scheduledTime();
        entity.cronExpression = task.getScheduleRule().cronExpression();
        entity.maxAttempts = task.getRetryPolicy().maxAttempts();
        entity.initialIntervalMs = task.getRetryPolicy().initialInterval().toMillis();
        entity.multiplier = task.getRetryPolicy().multiplier();
        entity.jitterFactor = task.getRetryPolicy().jitterFactor();
        entity.retryCount = task.getRetryCount();

        if (task.getCurrentLease() != null) {
            entity.leaseOwner = task.getCurrentLease().nodeOwner();
            entity.leaseExpiresAt = task.getCurrentLease().expiresAt();
        } else {
            entity.leaseOwner = null;
            entity.leaseExpiresAt = null;
        }

        entity.version = task.getVersion();
        entity.createdAt = task.getCreatedAt();
        entity.updatedAt = task.getUpdatedAt();
        return entity;
    }

    public Task toDomain() {
        Map<String, String> parsedHeaders = Collections.emptyMap();
        if (headers != null && !headers.isBlank()) {
            try {
                parsedHeaders = MAPPER.readValue(headers, new TypeReference<Map<String, String>>() {});
            } catch (Exception ignore) {}
        }

        ExecutionLease lease = null;
        if (leaseOwner != null && leaseExpiresAt != null) {
            lease = new ExecutionLease(leaseOwner, updatedAt, leaseExpiresAt);
        }

        return Task.reconstitute(
                TaskId.of(id),
                idempotencyKey,
                partitionBucket,
                type,
                new ScheduleRule(scheduledTimeUtc, cronExpression),
                new TaskPayload(target, parsedHeaders, payload),
                new RetryPolicy(maxAttempts, Duration.ofMillis(initialIntervalMs), multiplier, jitterFactor),
                status,
                retryCount,
                lease,
                version,
                createdAt,
                updatedAt
        );
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getPartitionBucket() { return partitionBucket; }
    public TaskType getType() { return type; }
    public TaskStatus getStatus() { return status; }
    public String getTarget() { return target; }
    public String getHeaders() { return headers; }
    public String getPayload() { return payload; }
    public Instant getScheduledTimeUtc() { return scheduledTimeUtc; }
    public String getCronExpression() { return cronExpression; }
    public int getMaxAttempts() { return maxAttempts; }
    public long getInitialIntervalMs() { return initialIntervalMs; }
    public double getMultiplier() { return multiplier; }
    public double getJitterFactor() { return jitterFactor; }
    public int getRetryCount() { return retryCount; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
