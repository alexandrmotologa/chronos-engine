package com.engine.chronos.infrastructure.adapter.in.scheduler;

import com.engine.chronos.domain.model.*;
import com.engine.chronos.domain.port.out.DispatchResult;
import com.engine.chronos.domain.port.out.DistributedLeasePort;
import com.engine.chronos.domain.port.out.DomainEventPublisherPort;
import com.engine.chronos.domain.port.out.TaskDispatcherPort;
import com.engine.chronos.domain.port.out.TaskRepositoryPort;
import com.engine.chronos.infrastructure.adapter.out.persistence.SpringDataTaskExecutionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartitionWorkerTest {

    @Mock
    private TaskRepositoryPort taskRepository;

    @Mock
    private DistributedLeasePort leasePort;

    @Mock
    private TaskDispatcherPort taskDispatcher;

    @Mock
    private SpringDataTaskExecutionRepository executionRepository;

    @Mock
    private DomainEventPublisherPort eventPublisher;

    private PartitionWorker worker;
    private final Instant now = Instant.parse("2026-09-10T12:00:00Z");

    @BeforeEach
    void setUp() {
        worker = new PartitionWorker(
                "test-node",
                5000,
                100,
                30,
                50,
                64,
                taskRepository,
                leasePort,
                taskDispatcher,
                executionRepository,
                eventPublisher
        );
        worker.init();
    }

    @AfterEach
    void tearDown() {
        worker.shutdown();
    }

    @Test
    @DisplayName("executeTask dispatches task and transitions to COMPLETED on success")
    void shouldExecuteTaskSuccessfully() {
        TaskId taskId = TaskId.generate();
        Task task = Task.create(
                taskId,
                "test-key",
                TaskType.WEBHOOK,
                ScheduleRule.at(now),
                TaskPayload.of("https://api.example.com", "{}"),
                RetryPolicy.defaultPolicy(),
                now
        );
        task.acquire("test-node", Duration.ofSeconds(5), now);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskDispatcher.dispatch(any())).thenReturn(DispatchResult.success(200, "OK", 30));

        worker.executeTask(taskId);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(taskDispatcher, times(1)).dispatch(any());
        verify(taskRepository, atLeast(1)).save(task);
        verify(executionRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("executeTask handles failure and transitions task to RETRY_PENDING")
    void shouldHandleExecutionFailureAndRetry() {
        TaskId taskId = TaskId.generate();
        Task task = Task.create(
                taskId,
                "test-key",
                TaskType.WEBHOOK,
                ScheduleRule.at(now),
                TaskPayload.of("https://api.example.com", "{}"),
                RetryPolicy.defaultPolicy(),
                now
        );
        task.acquire("test-node", Duration.ofSeconds(5), now);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskDispatcher.dispatch(any())).thenReturn(DispatchResult.failure(503, "Service Unavailable", 100));

        worker.executeTask(taskId);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.RETRY_PENDING);
        assertThat(task.getRetryCount()).isEqualTo(1);
        verify(taskDispatcher, times(1)).dispatch(any());
        verify(taskRepository, atLeast(1)).save(task);
        verify(executionRepository, times(1)).save(any());
    }
}
