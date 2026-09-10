package com.engine.chronos.application.service;

import com.engine.chronos.domain.event.DomainEvent;
import com.engine.chronos.domain.exception.TaskNotFoundException;
import com.engine.chronos.domain.model.*;
import com.engine.chronos.domain.port.in.CancelTaskUseCase;
import com.engine.chronos.domain.port.in.FireTaskNowUseCase;
import com.engine.chronos.domain.port.in.ScheduleTaskCommand;
import com.engine.chronos.domain.port.in.ScheduleTaskUseCase;
import com.engine.chronos.domain.port.out.DomainEventPublisherPort;
import com.engine.chronos.domain.port.out.TaskRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TaskCommandService implements ScheduleTaskUseCase, CancelTaskUseCase, FireTaskNowUseCase {

    private static final Logger log = LoggerFactory.getLogger(TaskCommandService.class);

    private final TaskRepositoryPort taskRepository;
    private final DomainEventPublisherPort eventPublisher;

    public TaskCommandService(TaskRepositoryPort taskRepository, DomainEventPublisherPort eventPublisher) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TaskId schedule(ScheduleTaskCommand command) {
        Instant now = Instant.now();

        // Check for idempotency match
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<Task> existing = taskRepository.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Task with idempotency key {} already exists with id {}", command.idempotencyKey(), existing.get().getId());
                return existing.get().getId();
            }
        }

        TaskId id = TaskId.generate();
        ScheduleRule scheduleRule = command.cronExpression() != null && !command.cronExpression().isBlank()
                ? ScheduleRule.cron(command.scheduledTime(), command.cronExpression())
                : ScheduleRule.at(command.scheduledTime());

        TaskPayload payload = TaskPayload.of(command.target(), command.headers(), command.payload());

        Task task = Task.create(
                id,
                command.idempotencyKey(),
                command.type(),
                scheduleRule,
                payload,
                command.retryPolicy(),
                now
        );

        Task saved = taskRepository.save(task);
        publishDomainEvents(task.pollEvents());

        log.info("Scheduled new task {} of type {} for partition {}", saved.getId(), saved.getType(), saved.getPartitionBucket());
        return saved.getId();
    }

    @Override
    public void cancel(TaskId taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        task.cancel(Instant.now());
        taskRepository.save(task);
        publishDomainEvents(task.pollEvents());
        log.info("Cancelled task {}", taskId);
    }

    @Override
    public void fireNow(TaskId taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        task.fireNow(Instant.now());
        taskRepository.save(task);
        log.info("Forced immediate firing for task {}", taskId);
    }

    private void publishDomainEvents(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            eventPublisher.publish(event);
        }
    }
}
