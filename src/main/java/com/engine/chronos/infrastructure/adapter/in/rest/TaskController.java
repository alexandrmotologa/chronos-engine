package com.engine.chronos.infrastructure.adapter.in.rest;

import com.engine.chronos.application.dto.*;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "Schedule, inspect, cancel, and control delayed tasks")
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
                request.retryPolicy() != null ? request.retryPolicy().toDomain() : null,
                request.tags()
        );

        TaskId id = commandService.schedule(command);
        URI location = URI.create("/api/v1/tasks/" + id);

        return ResponseEntity.created(location).body(Map.of(
                "id", id.value(),
                "status", "SCHEDULED",
                "location", location.toString()
        ));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Schedule multiple tasks atomically in a single batch")
    public ResponseEntity<BulkScheduleResponse> scheduleBulk(@Valid @RequestBody BulkScheduleTaskRequest bulkRequest) {
        List<ScheduleTaskCommand> commands = bulkRequest.tasks().stream()
                .map(ScheduleTaskRequest::toCommand)
                .toList();

        List<TaskId> ids = commandService.scheduleBulk(commands);
        List<UUID> uuidList = ids.stream().map(TaskId::value).toList();

        return ResponseEntity.ok(new BulkScheduleResponse(
                bulkRequest.tasks().size(),
                ids.size(),
                uuidList,
                List.of()
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

    @PostMapping("/{id}/reschedule")
    @Operation(summary = "Reschedule a task to a new target execution time")
    public ResponseEntity<Void> rescheduleTask(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleTaskRequest request
    ) {
        commandService.reschedule(TaskId.of(id), request.newScheduledTime());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause a scheduled task")
    public ResponseEntity<Void> pauseTask(@PathVariable UUID id) {
        commandService.pause(TaskId.of(id));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a paused task")
    public ResponseEntity<Void> resumeTask(@PathVariable UUID id) {
        commandService.resume(TaskId.of(id));
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @Operation(summary = "Query tasks by tag")
    public ResponseEntity<List<TaskResponse>> getTasks(@RequestParam(value = "tag", required = false) String tag) {
        if (tag != null && !tag.isBlank()) {
            return ResponseEntity.ok(queryService.getTasksByTag(tag));
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/timeline")
    @Operation(summary = "Get upcoming tasks for Gantt timeline visualization")
    public ResponseEntity<List<TaskResponse>> getTimeline(
            @RequestParam(value = "windowSec", defaultValue = "60") int windowSec
    ) {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant horizon = now.plusSeconds(windowSec);
        return ResponseEntity.ok(queryService.getUpcomingTasks(now, horizon, 50));
    }

    @DeleteMapping
    @Operation(summary = "Cancel tasks matching a tag")
    public ResponseEntity<CancelByTagResponse> cancelByTag(@RequestParam("tag") String tag) {
        int cancelled = commandService.cancelByTag(tag);
        return ResponseEntity.ok(new CancelByTagResponse(tag, cancelled));
    }
}
