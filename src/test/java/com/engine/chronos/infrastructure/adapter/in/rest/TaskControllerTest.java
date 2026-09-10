package com.engine.chronos.infrastructure.adapter.in.rest;

import com.engine.chronos.application.dto.TaskResponse;
import com.engine.chronos.application.service.TaskCommandService;
import com.engine.chronos.application.service.TaskQueryService;
import com.engine.chronos.domain.exception.TaskNotFoundException;
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
import java.util.Optional;
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
                "payload": "{\\"orderId\\": 1234}"
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
