package com.engine.chronos.application.dto;

import java.util.List;
import java.util.UUID;

public record BulkScheduleResponse(
        int total,
        int scheduled,
        List<UUID> taskIds,
        List<String> errors
) {}
