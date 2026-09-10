package com.engine.chronos.domain.event;

import com.engine.chronos.domain.model.TaskId;
import java.time.Instant;
import java.util.Objects;

public record TaskScheduledEvent(
        TaskId taskId,
        String idempotencyKey,
        Instant scheduledTime,
        Instant occurredAt
) implements DomainEvent {

    public TaskScheduledEvent {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
