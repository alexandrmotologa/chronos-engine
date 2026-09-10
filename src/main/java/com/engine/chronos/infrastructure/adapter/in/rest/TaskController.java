package com.engine.chronos.infrastructure.adapter.in.rest;

import com.engine.chronos.application.dto.ScheduleTaskRequest;
import com.engine.chronos.application.dto.TaskResponse;
import com.engine.chronos.application.service.TaskCommandService;
import com.engine.chronos.application.service.TaskQueryService;
import com.engine.chronos.domain.exception.TaskNotFoundException;
import com.engine.chronos.domain.model.TaskId;
import com.engine.chronos.domain.port.in.ScheduleTaskCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "Schedule, inspect, cancel, and manually trigger delayed tasks")
public class TaskController {

    private final TaskCommandService commandService;
    private final TaskQueryService queryService;

    public TaskController(TaskCommandService commandService, TaskQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    @Operation(summary = "Schedule a new delayed task")
    public ResponseEntity<Map<String, Object>> scheduleTask(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody ScheduleTaskRequest request
    ) {
        String effectiveKey = request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
                ? request.idempotencyKey()
                : idempotencyKeyHeader;

        ScheduleTaskCommand command = new ScheduleTaskCommand(
                effectiveKey,
                request.type(),
                request.target(),
                request.headers(),
                request.payload(),
                request.scheduledTime(),
                request.cronExpression(),
                request.retryPolicy() != null ? request.retryPolicy().toDomain() : null
        );

        TaskId id = commandService.schedule(command);
        URI location = URI.create("/api/v1/tasks/" + id);

        return ResponseEntity.created(location).body(Map.of(
                "id", id.value(),
                "status", "SCHEDULED",
                "location", location.toString()
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Inspect task details and execution history")
    public ResponseEntity<TaskResponse> getTask(@PathVariable UUID id) {
        return queryService.getTaskDetails(TaskId.of(id))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new TaskNotFoundException(TaskId.of(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a scheduled task")
    public ResponseEntity<Void> cancelTask(@PathVariable UUID id) {
        commandService.cancel(TaskId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/fire-now")
    @Operation(summary = "Trigger immediate task execution")
    public ResponseEntity<Void> fireNow(@PathVariable UUID id) {
        commandService.fireNow(TaskId.of(id));
        return ResponseEntity.accepted().build();
    }
}
