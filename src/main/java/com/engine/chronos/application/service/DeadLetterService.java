package com.engine.chronos.application.service;

import com.engine.chronos.application.dto.TaskResponse;
import com.engine.chronos.domain.event.DomainEvent;
import com.engine.chronos.domain.exception.TaskNotFoundException;
import com.engine.chronos.domain.model.Task;
import com.engine.chronos.domain.model.TaskId;
import com.engine.chronos.domain.port.out.DomainEventPublisherPort;
import com.engine.chronos.domain.port.out.TaskRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final TaskRepositoryPort taskRepository;
    private final DomainEventPublisherPort eventPublisher;

    public DeadLetterService(TaskRepositoryPort taskRepository, DomainEventPublisherPort eventPublisher) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listDeadLetters(int page, int size) {
        List<Task> deadLetters = taskRepository.findDeadLetters(page, size);
        long totalCount = taskRepository.countDeadLetters();

        List<TaskResponse> responses = deadLetters.stream()
                .map(t -> TaskResponse.from(t, Collections.emptyList()))
                .toList();

        return Map.of(
                "tasks", responses,
                "totalCount", totalCount,
                "page", page,
                "size", size
        );
    }

    public TaskResponse redrive(TaskId taskId, Instant targetTime) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        Instant now = Instant.now();
        Instant effectiveTarget = targetTime != null ? targetTime : now;

        task.redrive(effectiveTarget, now);
        Task saved = taskRepository.save(task);

        for (DomainEvent event : task.pollEvents()) {
            eventPublisher.publish(event);
        }

        log.info("Redriven task {} from dead letter queue, scheduled for {}", taskId, effectiveTarget);
        return TaskResponse.from(saved, Collections.emptyList());
    }
}
