package com.engine.chronos.application.service;

import com.engine.chronos.domain.event.DomainEvent;
import com.engine.chronos.domain.exception.TaskNotFoundException;
import com.engine.chronos.domain.model.*;
import com.engine.chronos.domain.port.in.*;
import com.engine.chronos.domain.port.out.DomainEventPublisherPort;
import com.engine.chronos.domain.port.out.TaskRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TaskCommandService implements
        ScheduleTaskUseCase,
        BulkScheduleTaskUseCase,
        CancelTaskUseCase,
        FireTaskNowUseCase,
        RescheduleTaskUseCase,
        PauseTaskUseCase,
        ResumeTaskUseCase,
        CancelByTagUseCase {

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
                command.tags(),
                now
        );

        Task saved = taskRepository.save(task);
        publishDomainEvents(task.pollEvents());

        log.info("Scheduled new task {} of type {} for partition {}", saved.getId(), saved.getType(), saved.getPartitionBucket());
        return saved.getId();
    }

    @Override
    public List<TaskId> scheduleBulk(List<ScheduleTaskCommand> commands) {
        Instant now = Instant.now();
        List<Task> tasksToSave = new ArrayList<>();
        List<TaskId> resultIds = new ArrayList<>();

        for (ScheduleTaskCommand command : commands) {
            if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
                Optional<Task> existing = taskRepository.findByIdempotencyKey(command.idempotencyKey());
                if (existing.isPresent()) {
                    resultIds.add(existing.get().getId());
                    continue;
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
                    command.tags(),
                    now
            );

            tasksToSave.add(task);
            resultIds.add(id);
        }

        if (!tasksToSave.isEmpty()) {
            List<Task> saved = taskRepository.saveAll(tasksToSave);
            for (Task task : tasksToSave) {
                publishDomainEvents(task.pollEvents());
            }
            log.info("Bulk scheduled {} tasks", saved.size());
        }

        return resultIds;
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

    @Override
    public void reschedule(TaskId taskId, Instant newScheduledTime) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        task.reschedule(newScheduledTime, Instant.now());
        taskRepository.save(task);
        publishDomainEvents(task.pollEvents());
        log.info("Rescheduled task {} to {}", taskId, newScheduledTime);
    }

    @Override
    public void pause(TaskId taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        task.pause(Instant.now());
        taskRepository.save(task);
        publishDomainEvents(task.pollEvents());
        log.info("Paused task {}", taskId);
    }

    @Override
    public void resume(TaskId taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        task.resume(Instant.now());
        taskRepository.save(task);
        publishDomainEvents(task.pollEvents());
        log.info("Resumed task {}", taskId);
    }

    @Override
    public int cancelByTag(String tag) {
        int count = taskRepository.cancelByTag(tag, Instant.now());
        log.info("Cancelled {} tasks with tag '{}'", count, tag);
        return count;
    }

    private void publishDomainEvents(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            eventPublisher.publish(event);
        }
    }
}
