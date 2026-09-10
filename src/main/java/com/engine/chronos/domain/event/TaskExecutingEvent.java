package com.engine.chronos.domain.event;

import com.engine.chronos.domain.model.TaskId;
import java.time.Instant;
import java.util.Objects;

public record TaskExecutingEvent(
        TaskId taskId,
        String nodeOwner,
        Instant occurredAt
) implements DomainEvent {

    public TaskExecutingEvent {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(nodeOwner, "nodeOwner must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
