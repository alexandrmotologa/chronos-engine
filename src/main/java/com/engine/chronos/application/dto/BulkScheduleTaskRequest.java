package com.engine.chronos.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkScheduleTaskRequest(
        @NotEmpty(message = "tasks list must not be empty")
        @Valid
        List<ScheduleTaskRequest> tasks
) {}
