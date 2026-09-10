package com.engine.chronos.infrastructure.adapter.in.rest;

import com.engine.chronos.domain.port.out.DistributedLeasePort;
import com.engine.chronos.domain.port.out.TaskRepositoryPort;
import com.engine.chronos.infrastructure.adapter.in.scheduler.PartitionWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {LiveEventStreamController.class, GlobalExceptionHandler.class})
class LiveEventStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PartitionWorker partitionWorker;

    @MockBean
    private DistributedLeasePort leasePort;

    @MockBean
    private TaskRepositoryPort taskRepository;

    @Test
    @DisplayName("GET /api/v1/cluster/status returns current node metrics and partition status")
    void shouldReturnClusterStatus() throws Exception {
        when(leasePort.getOwnedPartitions(anyString())).thenReturn(List.of(1, 2, 3));
        when(taskRepository.countDeadLetters()).thenReturn(0L);

        mockMvc.perform(get("/api/v1/cluster/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.ownedPartitionsCount").value(3))
                .andExpect(jsonPath("$.deadLettersCount").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/tasks/live establishes SSE connection")
    void shouldConnectToLiveStream() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/live"))
                .andExpect(status().isOk());
    }
}
