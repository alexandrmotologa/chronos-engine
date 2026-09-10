package com.engine.chronos.domain.event;

import com.engine.chronos.domain.model.TaskId;
import java.time.Instant;
import java.util.Objects;

public record TaskExecutedEvent(
        TaskId taskId,
        int durationMs,
        Instant occurredAt
) implements DomainEvent {

    public TaskExecutedEvent {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
