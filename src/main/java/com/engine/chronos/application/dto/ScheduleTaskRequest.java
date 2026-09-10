package com.engine.chronos.application.dto;

import com.engine.chronos.domain.model.TaskType;
import com.engine.chronos.domain.port.in.ScheduleTaskCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

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
        RetryPolicyConfig retryPolicy
) {
    public ScheduleTaskCommand toCommand() {
        return new ScheduleTaskCommand(
                idempotencyKey,
                type != null ? type : TaskType.WEBHOOK,
                target,
                headers,
                payload != null ? payload : "",
                scheduledTime,
                cronExpression,
                retryPolicy != null ? retryPolicy.toDomain() : null
        );
    }
}
