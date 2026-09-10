package com.engine.chronos.domain.event;

import com.engine.chronos.domain.model.TaskId;
import java.time.Instant;
import java.util.Objects;

public record TaskDeadLetteredEvent(
        TaskId taskId,
        int totalAttempts,
        String lastError,
        Instant occurredAt
) implements DomainEvent {

    public TaskDeadLetteredEvent {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
