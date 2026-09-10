package com.engine.chronos.infrastructure.adapter.in.rest;

import com.engine.chronos.application.dto.TaskResponse;
import com.engine.chronos.application.service.DeadLetterService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DeadLetterController.class, GlobalExceptionHandler.class})
class DeadLetterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeadLetterService dlqService;

    @Test
    @DisplayName("GET /api/v1/dlq returns paginated dead letter list")
    void shouldListDeadLetters() throws Exception {
        when(dlqService.listDeadLetters(0, 20)).thenReturn(Map.of(
                "tasks", List.of(),
                "totalCount", 0L,
                "page", 0,
                "size", 20
        ));

        mockMvc.perform(get("/api/v1/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.tasks").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/dlq/{id}/redrive returns 200 with rescheduled task")
    void shouldRedriveTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskResponse response = new TaskResponse(
                taskId,
                "test-dlq",
                55,
                TaskType.WEBHOOK,
                TaskStatus.SCHEDULED,
                "https://api.merchant.com/retry",
                Instant.now().plusSeconds(10),
                0,
                3,
                null,
                null,
                Instant.now(),
                Instant.now(),
                Collections.emptyList()
        );

        when(dlqService.redrive(eq(TaskId.of(taskId)), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/dlq/{id}/redrive", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledTime\":\"2026-09-10T16:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.retryCount").value(0));
    }
}
