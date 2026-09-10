package com.engine.chronos.domain;

import com.engine.chronos.domain.event.*;
import com.engine.chronos.domain.exception.IllegalTaskStateException;
import com.engine.chronos.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class TaskTest {

    private final Instant now = Instant.parse("2026-09-10T12:00:00Z");

    private Task createSampleTask(int maxAttempts) {
        return Task.create(
                TaskId.generate(),
                "order-exp-100",
                TaskType.WEBHOOK,
                ScheduleRule.at(now.plusSeconds(60)),
                TaskPayload.of("https://api.example.com/callback", Map.of("X-Trace", "123"), "{\"id\":100}"),
                new RetryPolicy(maxAttempts, Duration.ofSeconds(1), 2.0, 0.0),
                now
        );
    }

    @Test
    @DisplayName("Creating task sets status to SCHEDULED and emits TaskScheduledEvent")
    void shouldCreateTaskSuccessfully() {
        Task task = createSampleTask(3);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(task.getRetryCount()).isEqualTo(0);
        assertThat(task.getCurrentLease()).isNull();
        assertThat(task.getPartitionBucket()).isBetween(0, 255);

        List<DomainEvent> events = task.pollEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskScheduledEvent.class);
        TaskScheduledEvent event = (TaskScheduledEvent) events.get(0);
        assertThat(event.taskId()).isEqualTo(task.getId());
        assertThat(event.idempotencyKey()).isEqualTo("order-exp-100");
    }

    @Test
    @DisplayName("Acquiring task sets status to ACQUIRED and emits TaskAcquiredEvent")
    void shouldAcquireTask() {
        Task task = createSampleTask(3);
        task.pollEvents(); // clear creation event

        task.acquire("node-worker-1", Duration.ofSeconds(10), now);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.ACQUIRED);
        assertThat(task.getCurrentLease()).isNotNull();
        assertThat(task.getCurrentLease().nodeOwner()).isEqualTo("node-worker-1");
        assertThat(task.getCurrentLease().expiresAt()).isEqualTo(now.plusSeconds(10));

        List<DomainEvent> events = task.pollEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskAcquiredEvent.class);
    }

    @Test
    @DisplayName("Starting execution sets status to EXECUTING")
    void shouldStartExecution() {
        Task task = createSampleTask(3);
        task.acquire("node-worker-1", Duration.ofSeconds(10), now);
        task.pollEvents();

        task.startExecution(now);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.EXECUTING);
        List<DomainEvent> events = task.pollEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskExecutingEvent.class);
    }

    @Test
    @DisplayName("Completing execution transitions task to COMPLETED and clears lease")
    void shouldCompleteExecution() {
        Task task = createSampleTask(3);
        task.acquire("node-1", Duration.ofSeconds(10), now);
        task.startExecution(now);
        task.pollEvents();

        task.complete(45, now.plusMillis(45));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getCurrentLease()).isNull();

        List<DomainEvent> events = task.pollEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskExecutedEvent.class);
        TaskExecutedEvent executedEvent = (TaskExecutedEvent) events.get(0);
        assertThat(executedEvent.durationMs()).isEqualTo(45);
    }

    @Test
    @DisplayName("Failing task with remaining attempts transitions to RETRY_PENDING")
    void shouldRetryOnFailure() {
        Task task = createSampleTask(3);
        task.acquire("node-1", Duration.ofSeconds(10), now);
        task.startExecution(now);
        task.pollEvents();

        task.fail("503 Service Unavailable", now);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.RETRY_PENDING);
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(task.getCurrentLease()).isNull();
        assertThat(task.getScheduleRule().scheduledTime()).isEqualTo(now.plusSeconds(1));

        List<DomainEvent> events = task.pollEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(TaskFailedEvent.class);
        assertThat(events.get(1)).isInstanceOf(TaskRetriedEvent.class);
    }

    @Test
    @DisplayName("Failing task with exhausted attempts transitions to DEAD_LETTER")
    void shouldTransitionToDeadLetter() {
        Task task = createSampleTask(1); // max 1 attempt
        task.acquire("node-1", Duration.ofSeconds(10), now);
        task.startExecution(now);
        task.pollEvents();

        task.fail("500 Internal Server Error", now);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DEAD_LETTER);
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(task.getCurrentLease()).isNull();

        List<DomainEvent> events = task.pollEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(TaskFailedEvent.class);
        assertThat(events.get(1)).isInstanceOf(TaskDeadLetteredEvent.class);
    }

    @Test
    @DisplayName("Cancelling task from SCHEDULED state succeeds")
    void shouldCancelScheduledTask() {
        Task task = createSampleTask(3);
        task.pollEvents();

        task.cancel(now);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        List<DomainEvent> events = task.pollEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskCancelledEvent.class);
    }

    @Test
    @DisplayName("Cancelling an already completed task throws IllegalTaskStateException")
    void shouldThrowWhenCancellingTerminalTask() {
        Task task = createSampleTask(3);
        task.acquire("node-1", Duration.ofSeconds(10), now);
        task.startExecution(now);
        task.complete(20, now);

        assertThatThrownBy(() -> task.cancel(now))
                .isInstanceOf(IllegalTaskStateException.class);
    }

    @Test
    @DisplayName("Redriving a dead letter task resets retry count and sets status to SCHEDULED")
    void shouldRedriveDeadLetterTask() {
        Task task = createSampleTask(1);
        task.acquire("node-1", Duration.ofSeconds(10), now);
        task.startExecution(now);
        task.fail("Connection refused", now);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DEAD_LETTER);
        task.pollEvents();

        Instant redriveTime = now.plusSeconds(30);
        task.redrive(redriveTime, now);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(task.getRetryCount()).isEqualTo(0);
        assertThat(task.getScheduleRule().scheduledTime()).isEqualTo(redriveTime);

        List<DomainEvent> events = task.pollEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskScheduledEvent.class);
    }
}
