package com.engine.chronos.application.dto;

import com.engine.chronos.domain.model.Task;
import com.engine.chronos.domain.model.TaskStatus;
import com.engine.chronos.domain.model.TaskType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String idempotencyKey,
        int partitionBucket,
        TaskType type,
        TaskStatus status,
        String target,
        Instant scheduledTime,
        int retryCount,
        int maxAttempts,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        List<TaskExecutionResponse> executions
) {
    public static TaskResponse from(Task task, List<TaskExecutionResponse> executions) {
        return new TaskResponse(
                task.getId().value(),
                task.getIdempotencyKey(),
                task.getPartitionBucket(),
                task.getType(),
                task.getStatus(),
                task.getPayload().target(),
                task.getScheduleRule().scheduledTime(),
                task.getRetryCount(),
                task.getRetryPolicy().maxAttempts(),
                task.getCurrentLease() != null ? task.getCurrentLease().nodeOwner() : null,
                task.getCurrentLease() != null ? task.getCurrentLease().expiresAt() : null,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                executions != null ? executions : Collections.emptyList()
        );
    }
}
