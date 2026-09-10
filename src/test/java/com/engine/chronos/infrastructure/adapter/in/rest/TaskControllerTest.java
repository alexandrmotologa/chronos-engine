package com.engine.chronos.infrastructure.adapter.in.rest;

import com.engine.chronos.application.dto.TaskResponse;
import com.engine.chronos.application.service.TaskCommandService;
import com.engine.chronos.application.service.TaskQueryService;
import com.engine.chronos.domain.model.TaskId;
import com.engine.chronos.domain.model.TaskStatus;
import com.engine.chronos.domain.model.TaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {TaskController.class, GlobalExceptionHandler.class})
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskCommandService commandService;

    @MockBean
    private TaskQueryService queryService;

    @Test
    @DisplayName("POST /api/v1/tasks schedules task and returns 201 Created with Location")
    void shouldScheduleTaskSuccessfully() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(commandService.schedule(any())).thenReturn(TaskId.of(taskId));

        String requestJson = """
            {
                "idempotencyKey": "order-cancel-1234",
                "type": "WEBHOOK",
                "target": "https://api.merchant.com/webhook",
                "scheduledTime": "2026-09-10T15:00:00Z",
                "payload": "{\\"orderId\\": 1234}",
                "tags": ["orders", "billing"]
            }
            """;

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/tasks/" + taskId))
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        verify(commandService, times(1)).schedule(any());
    }

    @Test
    @DisplayName("POST /api/v1/tasks/bulk schedules multiple tasks and returns 200 OK")
    void shouldScheduleBulkTasksSuccessfully() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(commandService.scheduleBulk(any())).thenReturn(List.of(TaskId.of(id1), TaskId.of(id2)));

        String bulkJson = """
            {
                "tasks": [
                    {
                        "target": "https://api.merchant.com/webhook",
                        "scheduledTime": "2026-09-10T15:00:00Z",
                        "payload": "{\\"item\\": 1}"
                    },
                    {
                        "target": "https://api.merchant.com/webhook",
                        "scheduledTime": "2026-09-10T15:00:10Z",
                        "payload": "{\\"item\\": 2}"
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/tasks/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.scheduled").value(2))
                .andExpect(jsonPath("$.taskIds").isArray());

        verify(commandService, times(1)).scheduleBulk(any());
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{id}/reschedule updates execution time")
    void shouldRescheduleTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        doNothing().when(commandService).reschedule(eq(TaskId.of(taskId)), any());

        String json = """
            {
                "newScheduledTime": "2026-09-10T16:00:00Z"
            }
            """;

        mockMvc.perform(post("/api/v1/tasks/{id}/reschedule", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(commandService, times(1)).reschedule(eq(TaskId.of(taskId)), any());
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{id}/pause pauses task")
    void shouldPauseTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        doNothing().when(commandService).pause(TaskId.of(taskId));

        mockMvc.perform(post("/api/v1/tasks/{id}/pause", taskId))
                .andExpect(status().isOk());

        verify(commandService, times(1)).pause(TaskId.of(taskId));
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{id}/resume resumes task")
    void shouldResumeTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        doNothing().when(commandService).resume(TaskId.of(taskId));

        mockMvc.perform(post("/api/v1/tasks/{id}/resume", taskId))
                .andExpect(status().isOk());

        verify(commandService, times(1)).resume(TaskId.of(taskId));
    }

    @Test
    @DisplayName("GET /api/v1/tasks?tag=orders returns filtered tasks")
    void shouldGetTasksByTag() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskResponse response = new TaskResponse(
                taskId,
                "order-1234",
                42,
                TaskType.WEBHOOK,
                TaskStatus.SCHEDULED,
                "https://api.merchant.com/webhook",
                Instant.parse("2026-09-10T15:00:00Z"),
                0,
                3,
                null,
                null,
                Set.of("orders"),
                Instant.now(),
                Instant.now(),
                Collections.emptyList()
        );

        when(queryService.getTasksByTag("orders")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/tasks").param("tag", "orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(taskId.toString()));
    }

    @Test
    @DisplayName("DELETE /api/v1/tasks?tag=orders cancels matching tasks")
    void shouldCancelTasksByTag() throws Exception {
        when(commandService.cancelByTag("orders")).thenReturn(5);

        mockMvc.perform(delete("/api/v1/tasks").param("tag", "orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag").value("orders"))
                .andExpect(jsonPath("$.cancelledCount").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/tasks/timeline returns upcoming tasks")
    void shouldGetTimeline() throws Exception {
        when(queryService.getUpcomingTasks(any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tasks/timeline").param("windowSec", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/tasks without target returns 400 Bad Request with ProblemDetail")
    void shouldReturnBadRequestWhenTargetIsMissing() throws Exception {
        String invalidJson = """
            {
                "scheduledTime": "2026-09-10T15:00:00Z"
            }
            """;

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.target").exists());
    }

    @Test
    @DisplayName("GET /api/v1/tasks/{id} returns 200 with TaskResponse")
    void shouldGetTaskSuccessfully() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskResponse response = new TaskResponse(
                taskId,
                "order-cancel-1234",
                42,
                TaskType.WEBHOOK,
                TaskStatus.SCHEDULED,
                "https://api.merchant.com/webhook",
                Instant.parse("2026-09-10T15:00:00Z"),
                0,
                3,
                null,
                null,
                Set.of("orders"),
                Instant.now(),
                Instant.now(),
                Collections.emptyList()
        );

        when(queryService.getTaskDetails(TaskId.of(taskId))).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.partitionBucket").value(42));
    }

    @Test
    @DisplayName("GET /api/v1/tasks/{id} returns 404 when not found")
    void shouldReturn404WhenNotFound() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(queryService.getTaskDetails(TaskId.of(taskId))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tasks/{id}", taskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Task Not Found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/tasks/{id} returns 204 No Content")
    void shouldCancelTaskSuccessfully() throws Exception {
        UUID taskId = UUID.randomUUID();
        doNothing().when(commandService).cancel(TaskId.of(taskId));

        mockMvc.perform(delete("/api/v1/tasks/{id}", taskId))
                .andExpect(status().isNoContent());

        verify(commandService, times(1)).cancel(TaskId.of(taskId));
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{id}/fire-now returns 202 Accepted")
    void shouldFireTaskNowSuccessfully() throws Exception {
        UUID taskId = UUID.randomUUID();
        doNothing().when(commandService).fireNow(TaskId.of(taskId));

        mockMvc.perform(post("/api/v1/tasks/{id}/fire-now", taskId))
                .andExpect(status().isAccepted());

        verify(commandService, times(1)).fireNow(TaskId.of(taskId));
    }
}
