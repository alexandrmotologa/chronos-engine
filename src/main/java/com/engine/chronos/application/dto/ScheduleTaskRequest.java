package com.engine.chronos.application.dto;

import com.engine.chronos.domain.model.TaskType;
import com.engine.chronos.domain.port.in.ScheduleTaskCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public record ScheduleTaskRequest(
        String idempotencyKey,
        TaskType type,
        @NotBlank(message = "target is required")
        String target,
        Map<String, String> headers,
        String payload,
        @NotNull(message = "scheduledTime is required")
        Instant scheduledTime,
        String cronExpression,
        RetryPolicyConfig retryPolicy,
        Set<String> tags
) {
    public ScheduleTaskRequest(
            String idempotencyKey,
            TaskType type,
            String target,
            Map<String, String> headers,
            String payload,
            Instant scheduledTime,
            String cronExpression,
            RetryPolicyConfig retryPolicy
    ) {
        this(idempotencyKey, type, target, headers, payload, scheduledTime, cronExpression, retryPolicy, Collections.emptySet());
    }

    public ScheduleTaskCommand toCommand() {
        return new ScheduleTaskCommand(
                idempotencyKey,
                type != null ? type : TaskType.WEBHOOK,
                target,
                headers,
                payload != null ? payload : "",
                scheduledTime,
                cronExpression,
                retryPolicy != null ? retryPolicy.toDomain() : null,
                tags != null ? tags : Collections.emptySet()
        );
    }
}
