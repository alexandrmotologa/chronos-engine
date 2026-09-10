package com.engine.chronos.domain.event;

import com.engine.chronos.domain.model.TaskId;

import java.time.Instant;
import java.util.Objects;

public record TaskPausedEvent(
        TaskId taskId,
        Instant occurredAt
) implements DomainEvent {

    public TaskPausedEvent {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
