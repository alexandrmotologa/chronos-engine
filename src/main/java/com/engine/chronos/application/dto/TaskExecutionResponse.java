package com.engine.chronos.application.dto;

import java.time.Instant;

public record TaskExecutionResponse(
        int attemptNumber,
        String status,
        int durationMs,
        Integer statusCode,
        String errorMessage,
        Instant executedAt
) {}
