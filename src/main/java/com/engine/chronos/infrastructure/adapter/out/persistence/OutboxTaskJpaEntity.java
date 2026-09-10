package com.engine.chronos.infrastructure.adapter.out.persistence;

import com.engine.chronos.domain.model.RetryPolicy;
import com.engine.chronos.domain.model.TaskType;
import com.engine.chronos.domain.port.in.ScheduleTaskCommand;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "chronos_outbox")
public class OutboxTaskJpaEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private TaskType taskType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String target;

    @Column(columnDefinition = "TEXT")
    private String headers;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "scheduled_time_utc", nullable = false)
    private Instant scheduledTimeUtc;

    @Column(name = "cron_expression", length = 120)
    private String cronExpression;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "initial_interval_ms")
    private Long initialIntervalMs;

    private Double multiplier;

    @Column(name = "jitter_factor")
    private Double jitterFactor;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public OutboxTaskJpaEntity() {}

    public static OutboxTaskJpaEntity create(
            String idempotencyKey,
            TaskType taskType,
            String target,
            Map<String, String> headersMap,
            String payload,
            Instant scheduledTimeUtc,
            String cronExpression,
            RetryPolicy retryPolicy,
            Set<String> tagSet
    ) {
        OutboxTaskJpaEntity entity = new OutboxTaskJpaEntity();
        entity.id = UUID.randomUUID();
        entity.idempotencyKey = idempotencyKey;
        entity.taskType = taskType != null ? taskType : TaskType.WEBHOOK;
        entity.target = target;
        entity.payload = payload;
        entity.scheduledTimeUtc = scheduledTimeUtc != null ? scheduledTimeUtc : Instant.now();
        entity.cronExpression = cronExpression;
        entity.status = "PENDING";
        entity.createdAt = Instant.now();

        if (headersMap != null && !headersMap.isEmpty()) {
            try {
                entity.headers = MAPPER.writeValueAsString(headersMap);
            } catch (Exception e) {
                entity.headers = null;
            }
        }

        if (tagSet != null && !tagSet.isEmpty()) {
            entity.tags = String.join(",", tagSet);
        }

        if (retryPolicy != null) {
            entity.maxAttempts = retryPolicy.maxAttempts();
            entity.initialIntervalMs = retryPolicy.initialInterval().toMillis();
            entity.multiplier = retryPolicy.multiplier();
            entity.jitterFactor = retryPolicy.jitterFactor();
        }

        return entity;
    }

    public ScheduleTaskCommand toCommand() {
        Map<String, String> headersMap = Collections.emptyMap();
        if (headers != null && !headers.isBlank()) {
            try {
                headersMap = MAPPER.readValue(headers, new TypeReference<>() {});
            } catch (Exception ignored) {}
        }

        Set<String> tagSet = Collections.emptySet();
        if (tags != null && !tags.isBlank()) {
            tagSet = new HashSet<>(Arrays.asList(tags.split(",")));
        }

        RetryPolicy policy = null;
        if (maxAttempts != null && initialIntervalMs != null) {
            policy = new RetryPolicy(
                    maxAttempts,
                    Duration.ofMillis(initialIntervalMs),
                    multiplier != null ? multiplier : 2.0,
                    jitterFactor != null ? jitterFactor : 0.2
            );
        }

        return new ScheduleTaskCommand(
                idempotencyKey,
                taskType,
                target,
                headersMap,
                payload != null ? payload : "",
                scheduledTimeUtc,
                cronExpression,
                policy,
                tagSet
        );
    }

    public void markProcessed(Instant now) {
        this.status = "PROCESSED";
        this.processedAt = now;
    }

    public void markFailed(String error, Instant now) {
        this.status = "FAILED";
        this.errorMessage = error;
        this.processedAt = now;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public TaskType getTaskType() { return taskType; }
    public String getTarget() { return target; }
    public String getPayload() { return payload; }
    public Instant getScheduledTimeUtc() { return scheduledTimeUtc; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
}
