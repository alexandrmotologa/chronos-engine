package com.engine.chronos.domain.event;

import com.engine.chronos.domain.model.TaskId;

import java.time.Instant;
import java.util.Objects;

public record TaskRescheduledEvent(
        TaskId taskId,
        Instant newScheduledTime,
        Instant occurredAt
) implements DomainEvent {

    public TaskRescheduledEvent {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(newScheduledTime, "newScheduledTime must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
