package com.engine.chronos.domain;

import com.engine.chronos.domain.event.TaskPausedEvent;
import com.engine.chronos.domain.event.TaskRescheduledEvent;
import com.engine.chronos.domain.event.TaskResumedEvent;
import com.engine.chronos.domain.exception.IllegalTaskStateException;
import com.engine.chronos.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskOperationalTest {

    private final Instant now = Instant.parse("2026-09-10T12:00:00Z");

    private Task createSampleTask() {
        return Task.create(
                TaskId.generate(),
                "order-key-1",
                TaskType.WEBHOOK,
                ScheduleRule.at(now.plusSeconds(60)),
                TaskPayload.of("https://api.example.com/callback", Map.of(), "{}"),
                new RetryPolicy(3, Duration.ofSeconds(1), 2.0, 0.0),
                Set.of("order-service", "critical"),
                now
        );
    }

    @Test
    @DisplayName("Task can be paused when SCHEDULED and emits TaskPausedEvent")
    void shouldPauseTask() {
        Task task = createSampleTask();
        task.pollEvents();

        task.pause(now);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.PAUSED);
        assertThat(task.pollEvents()).hasSize(1);
        assertThat(task.pollEvents()).isEmpty();
    }

    @Test
    @DisplayName("Task can be resumed when PAUSED and emits TaskResumedEvent")
    void shouldResumeTask() {
        Task task = createSampleTask();
        task.pause(now);
        task.pollEvents();

        task.resume(now.plusSeconds(5));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        var events = task.pollEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskResumedEvent.class);
    }

    @Test
    @DisplayName("Cannot resume task if not PAUSED")
    void shouldFailResumingNonPausedTask() {
        Task task = createSampleTask();
        assertThatThrownBy(() -> task.resume(now))
                .isInstanceOf(IllegalTaskStateException.class);
    }

    @Test
    @DisplayName("Task can be rescheduled and updates scheduled time and emits TaskRescheduledEvent")
    void shouldRescheduleTask() {
        Task task = createSampleTask();
        task.pollEvents();

        Instant newTime = now.plusSeconds(120);
        task.reschedule(newTime, now);

        assertThat(task.getScheduleRule().scheduledTime()).isEqualTo(newTime);
        var events = task.pollEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TaskRescheduledEvent.class);
        TaskRescheduledEvent event = (TaskRescheduledEvent) events.get(0);
        assertThat(event.newScheduledTime()).isEqualTo(newTime);
    }

    @Test
    @DisplayName("Cannot reschedule terminal task")
    void shouldFailReschedulingTerminalTask() {
        Task task = createSampleTask();
        task.cancel(now);

        assertThatThrownBy(() -> task.reschedule(now.plusSeconds(30), now))
                .isInstanceOf(IllegalTaskStateException.class);
    }

    @Test
    @DisplayName("Task tags are properly stored and immutable")
    void shouldStoreTags() {
        Task task = createSampleTask();
        assertThat(task.getTags()).containsExactlyInAnyOrder("order-service", "critical");
    }
}
