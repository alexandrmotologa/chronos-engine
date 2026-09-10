package com.engine.chronos.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chronos_executions")
public class TaskExecutionJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    public TaskExecutionJpaEntity() {}

    public TaskExecutionJpaEntity(
            UUID id,
            UUID taskId,
            int attemptNumber,
            String status,
            int durationMs,
            Integer statusCode,
            String responseBody,
            String errorMessage,
            Instant executedAt
    ) {
        this.id = id;
        this.taskId = taskId;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.durationMs = durationMs;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.errorMessage = errorMessage;
        this.executedAt = executedAt;
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getStatus() { return status; }
    public int getDurationMs() { return durationMs; }
    public Integer getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getExecutedAt() { return executedAt; }
}
