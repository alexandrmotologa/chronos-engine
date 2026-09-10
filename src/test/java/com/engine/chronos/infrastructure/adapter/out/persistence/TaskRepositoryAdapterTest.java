package com.engine.chronos.infrastructure.adapter.out.persistence;

import com.engine.chronos.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TaskRepositoryAdapter.class)
class TaskRepositoryAdapterTest {

    @Autowired
    private TaskRepositoryAdapter repositoryAdapter;

    private final Instant now = Instant.parse("2026-09-10T12:00:00Z");

    @Test
    @DisplayName("Persisting and finding a Task round-trips correctly")
    void shouldPersistAndFindTask() {
        TaskId taskId = TaskId.generate();
        Task task = Task.create(
                taskId,
                "test-order-999",
                TaskType.WEBHOOK,
                ScheduleRule.at(now.plusSeconds(30)),
                TaskPayload.of("https://api.example.com/webhook", Map.of("X-Trace-Id", "trace-123"), "{\"order\":999}"),
                RetryPolicy.defaultPolicy(),
                now
        );

        Task saved = repositoryAdapter.save(task);
        assertThat(saved.getId()).isEqualTo(taskId);

        Optional<Task> found = repositoryAdapter.findById(taskId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(taskId);
        assertThat(found.get().getIdempotencyKey()).isEqualTo("test-order-999");
        assertThat(found.get().getPayload().target()).isEqualTo("https://api.example.com/webhook");
        assertThat(found.get().getPayload().headers()).containsEntry("X-Trace-Id", "trace-123");
        assertThat(found.get().getVersion()).isNotNull();
    }

    @Test
    @DisplayName("findDueTasks returns tasks scheduled before cutoff within the partition")
    void shouldFindDueTasksInPartition() {
        TaskId task1 = TaskId.generate();
        Task t1 = Task.create(
                task1,
                "key-partition-test-1",
                TaskType.WEBHOOK,
                ScheduleRule.at(now.plusSeconds(10)),
                TaskPayload.of("https://api.example.com", "{}"),
                RetryPolicy.defaultPolicy(),
                now
        );

        repositoryAdapter.save(t1);

        int partition = t1.getPartitionBucket();

        List<Task> dueTasks = repositoryAdapter.findDueTasks(partition, now.plusSeconds(15), 10);
        assertThat(dueTasks).extracting(Task::getId).contains(task1);

        List<Task> notDueTasks = repositoryAdapter.findDueTasks(partition, now.plusSeconds(5), 10);
        assertThat(notDueTasks).isEmpty();
    }

    @Test
    @DisplayName("resetExpiredLeases resets orphaned tasks to SCHEDULED")
    void shouldResetExpiredLeases() {
        TaskId taskId = TaskId.generate();
        Task task = Task.create(
                taskId,
                "key-lease-test-1",
                TaskType.WEBHOOK,
                ScheduleRule.at(now),
                TaskPayload.of("https://api.example.com", "{}"),
                RetryPolicy.defaultPolicy(),
                now
        );

        task.acquire("crashed-node", Duration.ofSeconds(5), now.minusSeconds(10));
        repositoryAdapter.save(task);

        int resetCount = repositoryAdapter.resetExpiredLeases(now);
        assertThat(resetCount).isEqualTo(1);

        Optional<Task> reloaded = repositoryAdapter.findById(taskId);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(reloaded.get().getCurrentLease()).isNull();
    }
}
