package com.engine.chronos.infrastructure.adapter.out.persistence;

import com.engine.chronos.domain.model.TaskId;
import com.engine.chronos.domain.model.TaskType;
import com.engine.chronos.domain.port.in.ScheduleTaskCommand;
import com.engine.chronos.domain.port.in.ScheduleTaskUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private SpringDataOutboxRepository outboxRepository;

    @Mock
    private ScheduleTaskUseCase scheduleTaskUseCase;

    @InjectMocks
    private OutboxPoller outboxPoller;

    @Test
    @DisplayName("OutboxPoller processes pending outbox tasks and marks them PROCESSED")
    void shouldProcessPendingTasks() {
        OutboxTaskJpaEntity entity = OutboxTaskJpaEntity.create(
                "idemp-outbox-1",
                TaskType.WEBHOOK,
                "https://api.example.com/webhook",
                Map.of("Authorization", "Bearer secret"),
                "{\"hello\":\"world\"}",
                Instant.now().plusSeconds(10),
                null,
                null,
                Set.of("finance", "outbox")
        );

        when(outboxRepository.findPending(any(Pageable.class))).thenReturn(List.of(entity));
        when(scheduleTaskUseCase.schedule(any(ScheduleTaskCommand.class))).thenReturn(TaskId.generate());

        outboxPoller.processOutbox();

        verify(scheduleTaskUseCase, times(1)).schedule(any(ScheduleTaskCommand.class));
        verify(outboxRepository, times(1)).saveAllAndFlush(any());

        assertThat(entity.getStatus()).isEqualTo("PROCESSED");
        assertThat(entity.getProcessedAt()).isNotNull();
    }
}
