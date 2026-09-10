package com.engine.chronos.domain.port.in;

import com.engine.chronos.domain.model.RetryPolicy;
import com.engine.chronos.domain.model.TaskType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ScheduleTaskCommand(
        String idempotencyKey,
        TaskType type,
        String target,
        Map<String, String> headers,
        String payload,
        Instant scheduledTime,
        String cronExpression,
        RetryPolicy retryPolicy
) {
    public ScheduleTaskCommand {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        retryPolicy = retryPolicy == null ? RetryPolicy.defaultPolicy() : retryPolicy;
    }
}
