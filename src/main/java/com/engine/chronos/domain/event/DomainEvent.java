package com.engine.chronos.domain.event;

import com.engine.chronos.domain.model.TaskId;
import java.time.Instant;

public sealed interface DomainEvent
        permits TaskScheduledEvent,
                TaskAcquiredEvent,
                TaskExecutingEvent,
                TaskExecutedEvent,
                TaskFailedEvent,
                TaskRetriedEvent,
                TaskDeadLetteredEvent,
                TaskCancelledEvent,
                TaskRescheduledEvent,
                TaskPausedEvent,
                TaskResumedEvent {

    TaskId taskId();

    Instant occurredAt();
}
