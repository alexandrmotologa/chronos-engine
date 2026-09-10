package com.engine.chronos.domain.port.out;

import com.engine.chronos.domain.model.Task;
import com.engine.chronos.domain.model.TaskId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskRepositoryPort {

    Task save(Task task);

    Optional<Task> findById(TaskId id);

    Optional<Task> findByIdempotencyKey(String idempotencyKey);

    List<Task> findDueTasks(int partitionBucket, Instant cutoff, int limit);

    int resetExpiredLeases(Instant now);

    List<Task> findDeadLetters(int page, int size);

    long countDeadLetters();

    List<Task> findUpcomingTasks(Instant from, Instant to, int limit);
}
