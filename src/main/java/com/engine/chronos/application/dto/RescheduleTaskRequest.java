package com.engine.chronos.application.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RescheduleTaskRequest(
        @NotNull(message = "newScheduledTime is required")
        Instant newScheduledTime
) {}
