package com.engine.chronos.domain.event;

import com.engine.chronos.domain.model.TaskId;
import java.time.Instant;
import java.util.Objects;

public record TaskAcquiredEvent(
        TaskId taskId,
        String nodeOwner,
        Instant leaseExpiresAt,
        Instant occurredAt
) implements DomainEvent {

    public TaskAcquiredEvent {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(nodeOwner, "nodeOwner must not be null");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
