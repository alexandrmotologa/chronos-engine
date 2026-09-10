package com.engine.chronos.application.service;

import com.engine.chronos.application.dto.TaskExecutionResponse;
import com.engine.chronos.application.dto.TaskResponse;
import com.engine.chronos.domain.model.Task;
import com.engine.chronos.domain.model.TaskId;
import com.engine.chronos.domain.port.in.GetTaskQuery;
import com.engine.chronos.domain.port.out.TaskRepositoryPort;
import com.engine.chronos.infrastructure.adapter.out.persistence.SpringDataTaskExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TaskQueryService implements GetTaskQuery {

    private final TaskRepositoryPort taskRepository;
    private final SpringDataTaskExecutionRepository executionRepository;

    public TaskQueryService(
            TaskRepositoryPort taskRepository,
            SpringDataTaskExecutionRepository executionRepository
    ) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
    }

    @Override
    public Optional<Task> getById(TaskId taskId) {
        return taskRepository.findById(taskId);
    }

    @Override
    public Optional<Task> getByIdempotencyKey(String idempotencyKey) {
        return taskRepository.findByIdempotencyKey(idempotencyKey);
    }

    public Optional<TaskResponse> getTaskDetails(TaskId taskId) {
        return taskRepository.findById(taskId).map(task -> {
            List<TaskExecutionResponse> executions = executionRepository
                    .findByTaskIdOrderByExecutedAtDesc(taskId.value())
                    .stream()
                    .map(e -> new TaskExecutionResponse(
                            e.getAttemptNumber(),
                            e.getStatus(),
                            e.getDurationMs(),
                            e.getStatusCode(),
                            e.getErrorMessage(),
                            e.getExecutedAt()
                    ))
                    .toList();

            return TaskResponse.from(task, executions);
        });
    }

    public List<TaskResponse> getTasksByTag(String tag) {
        return taskRepository.findByTag(tag)
                .stream()
                .map(t -> TaskResponse.from(t, Collections.emptyList()))
                .toList();
    }

    public List<TaskResponse> getUpcomingTasks(Instant from, Instant to, int limit) {
        return taskRepository.findUpcomingTasks(from, to, limit)
                .stream()
                .map(t -> TaskResponse.from(t, Collections.emptyList()))
                .toList();
    }
}
