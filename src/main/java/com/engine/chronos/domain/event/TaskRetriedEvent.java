package com.engine.chronos.domain.event;

import com.engine.chronos.domain.model.TaskId;
import java.time.Instant;
import java.util.Objects;

public record TaskRetriedEvent(
        TaskId taskId,
        int nextAttempt,
        Instant nextScheduledTime,
        Instant occurredAt
) implements DomainEvent {

    public TaskRetriedEvent {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(nextScheduledTime, "nextScheduledTime must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
