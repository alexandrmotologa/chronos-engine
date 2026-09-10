package com.engine.chronos.infrastructure.adapter.out.persistence;

import com.engine.chronos.domain.model.Task;
import com.engine.chronos.domain.model.TaskId;
import com.engine.chronos.domain.model.TaskStatus;
import com.engine.chronos.domain.port.out.TaskRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class TaskRepositoryAdapter implements TaskRepositoryPort {

    private final SpringDataTaskRepository taskRepository;

    public TaskRepositoryAdapter(SpringDataTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    @Transactional
    public Task save(Task task) {
        TaskJpaEntity entity = TaskJpaEntity.fromDomain(task);
        TaskJpaEntity saved = taskRepository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Task> findById(TaskId id) {
        return taskRepository.findById(id.value()).map(TaskJpaEntity::toDomain);
    }

    @Override
    public Optional<Task> findByIdempotencyKey(String idempotencyKey) {
        return taskRepository.findByIdempotencyKey(idempotencyKey).map(TaskJpaEntity::toDomain);
    }

    @Override
    public List<Task> findDueTasks(int partitionBucket, Instant cutoff, int limit) {
        return taskRepository.findDueTasks(partitionBucket, cutoff, PageRequest.of(0, limit))
                .stream()
                .map(TaskJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int resetExpiredLeases(Instant now) {
        return taskRepository.resetExpiredLeases(now);
    }

    @Override
    public List<Task> findDeadLetters(int page, int size) {
        return taskRepository.findByStatusOrderByUpdatedAtDesc(TaskStatus.DEAD_LETTER, PageRequest.of(page, size))
                .stream()
                .map(TaskJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countDeadLetters() {
        return taskRepository.countByStatus(TaskStatus.DEAD_LETTER);
    }

    @Override
    public List<Task> findUpcomingTasks(Instant from, Instant to, int limit) {
        return taskRepository.findByScheduledTimeUtcBetweenOrderByScheduledTimeUtcAsc(from, to, PageRequest.of(0, limit))
                .stream()
                .map(TaskJpaEntity::toDomain)
                .toList();
    }
}
