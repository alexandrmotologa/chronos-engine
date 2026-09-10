package com.engine.chronos.infrastructure.adapter.in.rest;

import com.engine.chronos.application.dto.TaskResponse;
import com.engine.chronos.application.service.DeadLetterService;
import com.engine.chronos.domain.model.TaskId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dlq")
@Tag(name = "Dead Letter Queue", description = "Audit and redrive tasks that exhausted retry limits")
public class DeadLetterController {

    private final DeadLetterService dlqService;

    public DeadLetterController(DeadLetterService dlqService) {
        this.dlqService = dlqService;
    }

    @GetMapping
    @Operation(summary = "List dead letter tasks with pagination")
    public ResponseEntity<Map<String, Object>> listDeadLetters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(dlqService.listDeadLetters(page, size));
    }

    @PostMapping("/{id}/redrive")
    @Operation(summary = "Redrive a failed task back into the scheduling pipeline")
    public ResponseEntity<TaskResponse> redriveTask(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        Instant targetTime = null;
        if (body != null && body.containsKey("scheduledTime") && !body.get("scheduledTime").isBlank()) {
            targetTime = Instant.parse(body.get("scheduledTime"));
        }

        TaskResponse redriven = dlqService.redrive(TaskId.of(id), targetTime);
        return ResponseEntity.ok(redriven);
    }
}
