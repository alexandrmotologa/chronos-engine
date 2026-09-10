package com.engine.chronos.domain.model;

import com.engine.chronos.domain.event.*;
import com.engine.chronos.domain.exception.IllegalTaskStateException;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Task implements Serializable {

    private final TaskId id;
    private final String idempotencyKey;
    private final int partitionBucket;
    private final TaskType type;
    private final TaskPayload payload;
    private final RetryPolicy retryPolicy;
    private final Instant createdAt;
    private final java.util.Set<String> tags;

    private ScheduleRule scheduleRule;
    private TaskStatus status;
    private int retryCount;
    private ExecutionLease currentLease;
    private Long version;
    private Instant updatedAt;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Task(
            TaskId id,
            String idempotencyKey,
            int partitionBucket,
            TaskType type,
            ScheduleRule scheduleRule,
            TaskPayload payload,
            RetryPolicy retryPolicy,
            TaskStatus status,
            int retryCount,
            ExecutionLease currentLease,
            Long version,
            java.util.Set<String> tags,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.idempotencyKey = idempotencyKey;
        this.partitionBucket = partitionBucket;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.scheduleRule = Objects.requireNonNull(scheduleRule, "scheduleRule must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.retryCount = retryCount;
        this.currentLease = currentLease;
        this.version = version;
        this.tags = tags != null ? new java.util.HashSet<>(tags) : new java.util.HashSet<>();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static Task create(
            TaskId id,
            String idempotencyKey,
            TaskType type,
            ScheduleRule scheduleRule,
            TaskPayload payload,
            RetryPolicy retryPolicy,
            Instant now
    ) {
        return create(id, idempotencyKey, type, scheduleRule, payload, retryPolicy, java.util.Collections.emptySet(), now);
    }

    public static Task create(
            TaskId id,
            String idempotencyKey,
            TaskType type,
            ScheduleRule scheduleRule,
            TaskPayload payload,
            RetryPolicy retryPolicy,
            java.util.Set<String> tags,
            Instant now
    ) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(scheduleRule, "scheduleRule must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        Objects.requireNonNull(now, "now must not be null");

        String routingKey = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey
                : id.toString();
        int partition = calculatePartitionBucket(routingKey);

        Task task = new Task(
                id,
                idempotencyKey,
                partition,
                type,
                scheduleRule,
                payload,
                retryPolicy,
                TaskStatus.SCHEDULED,
                0,
                null,
                null,
                tags,
                now,
                now
        );

        task.recordEvent(new TaskScheduledEvent(id, idempotencyKey, scheduleRule.scheduledTime(), now));
        return task;
    }

    public static Task reconstitute(
            TaskId id,
            String idempotencyKey,
            int partitionBucket,
            TaskType type,
            ScheduleRule scheduleRule,
            TaskPayload payload,
            RetryPolicy retryPolicy,
            TaskStatus status,
            int retryCount,
            ExecutionLease currentLease,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return reconstitute(id, idempotencyKey, partitionBucket, type, scheduleRule, payload, retryPolicy, status, retryCount, currentLease, version, java.util.Collections.emptySet(), createdAt, updatedAt);
    }

    public static Task reconstitute(
            TaskId id,
            String idempotencyKey,
            int partitionBucket,
            TaskType type,
            ScheduleRule scheduleRule,
            TaskPayload payload,
            RetryPolicy retryPolicy,
            TaskStatus status,
            int retryCount,
            ExecutionLease currentLease,
            Long version,
            java.util.Set<String> tags,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Task(
                id,
                idempotencyKey,
                partitionBucket,
                type,
                scheduleRule,
                payload,
                retryPolicy,
                status,
                retryCount,
                currentLease,
                version,
                tags,
                createdAt,
                updatedAt
        );
    }

    public static int calculatePartitionBucket(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Math.abs(key.hashCode() % 256);
    }

    public void acquire(String nodeOwner, Duration leaseDuration, Instant now) {
        Objects.requireNonNull(nodeOwner, "nodeOwner must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        Objects.requireNonNull(now, "now must not be null");

        boolean isExpired = currentLease != null && currentLease.isExpired(now);
        if (!status.isEligibleForAcquisition() && !isExpired) {
            throw new IllegalTaskStateException(id, status, "acquire");
        }

        this.currentLease = ExecutionLease.of(nodeOwner, leaseDuration, now);
        this.status = TaskStatus.ACQUIRED;
        this.updatedAt = now;

        recordEvent(new TaskAcquiredEvent(id, nodeOwner, currentLease.expiresAt(), now));
    }

    public void startExecution(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != TaskStatus.ACQUIRED) {
            throw new IllegalTaskStateException(id, status, "startExecution");
        }

        this.status = TaskStatus.EXECUTING;
        this.updatedAt = now;

        String owner = currentLease != null ? currentLease.nodeOwner() : "unknown";
        recordEvent(new TaskExecutingEvent(id, owner, now));
    }

    public void complete(int durationMs, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != TaskStatus.EXECUTING) {
            throw new IllegalTaskStateException(id, status, "complete");
        }

        this.status = TaskStatus.COMPLETED;
        this.currentLease = null;
        this.updatedAt = now;

        recordEvent(new TaskExecutedEvent(id, durationMs, now));
    }

    public void fail(String reason, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != TaskStatus.EXECUTING && status != TaskStatus.ACQUIRED) {
            throw new IllegalTaskStateException(id, status, "fail");
        }

        this.retryCount++;
        this.updatedAt = now;

        recordEvent(new TaskFailedEvent(id, retryCount, reason, now));

        if (retryCount < retryPolicy.maxAttempts()) {
            Duration backoff = retryPolicy.calculateBackoff(retryCount);
            Instant nextRun = now.plus(backoff);
            this.scheduleRule = ScheduleRule.at(nextRun);
            this.status = TaskStatus.RETRY_PENDING;
            this.currentLease = null;
            recordEvent(new TaskRetriedEvent(id, retryCount, nextRun, now));
        } else {
            this.status = TaskStatus.DEAD_LETTER;
            this.currentLease = null;
            recordEvent(new TaskDeadLetteredEvent(id, retryCount, reason, now));
        }
    }

    public void cancel(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!status.isCancellable()) {
            throw new IllegalTaskStateException(id, status, "cancel");
        }

        this.status = TaskStatus.CANCELLED;
        this.currentLease = null;
        this.updatedAt = now;

        recordEvent(new TaskCancelledEvent(id, now));
    }

    public void redrive(Instant targetTime, Instant now) {
        Objects.requireNonNull(targetTime, "targetTime must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (status != TaskStatus.DEAD_LETTER) {
            throw new IllegalTaskStateException(id, status, "redrive");
        }

        this.status = TaskStatus.SCHEDULED;
        this.retryCount = 0;
        this.scheduleRule = ScheduleRule.at(targetTime);
        this.currentLease = null;
        this.updatedAt = now;

        recordEvent(new TaskScheduledEvent(id, idempotencyKey, targetTime, now));
    }

    public void renewLease(Duration duration, Instant now) {
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (currentLease == null) {
            throw new IllegalTaskStateException(id, status, "renewLease with no lease");
        }

        this.currentLease = currentLease.renew(duration, now);
        this.updatedAt = now;
    }

    public void releaseLease(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        this.currentLease = null;
        this.status = TaskStatus.SCHEDULED;
        this.updatedAt = now;
    }

    public void reschedule(Instant newScheduledTime, Instant now) {
        Objects.requireNonNull(newScheduledTime, "newScheduledTime must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (status.isTerminal()) {
            throw new IllegalTaskStateException(id, status, "reschedule");
        }

        this.scheduleRule = ScheduleRule.at(newScheduledTime);
        this.currentLease = null;
        if (status == TaskStatus.ACQUIRED) {
            this.status = TaskStatus.SCHEDULED;
        }
        this.updatedAt = now;

        recordEvent(new TaskRescheduledEvent(id, newScheduledTime, now));
    }

    public void pause(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        if (!status.isPausable()) {
            throw new IllegalTaskStateException(id, status, "pause");
        }

        this.status = TaskStatus.PAUSED;
        this.currentLease = null;
        this.updatedAt = now;

        recordEvent(new TaskPausedEvent(id, now));
    }

    public void resume(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        if (status != TaskStatus.PAUSED) {
            throw new IllegalTaskStateException(id, status, "resume");
        }

        this.status = TaskStatus.SCHEDULED;
        this.updatedAt = now;

        recordEvent(new TaskResumedEvent(id, now));
    }

    public void fireNow(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!status.isEligibleForAcquisition()) {
            throw new IllegalTaskStateException(id, status, "fireNow");
        }
        this.scheduleRule = ScheduleRule.at(now);
        this.updatedAt = now;
    }

    private void recordEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    public List<DomainEvent> pollEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return Collections.unmodifiableList(events);
    }

    // Getters
    public TaskId getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getPartitionBucket() { return partitionBucket; }
    public TaskType getType() { return type; }
    public ScheduleRule getScheduleRule() { return scheduleRule; }
    public TaskPayload getPayload() { return payload; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public TaskStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public ExecutionLease getCurrentLease() { return currentLease; }
    public Long getVersion() { return version; }
    public java.util.Set<String> getTags() { return Collections.unmodifiableSet(tags); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
