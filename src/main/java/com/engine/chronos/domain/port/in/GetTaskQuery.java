package com.engine.chronos.domain.port.in;

import com.engine.chronos.domain.model.Task;
import com.engine.chronos.domain.model.TaskId;

import java.util.Optional;

public interface GetTaskQuery {
    Optional<Task> getById(TaskId taskId);
    Optional<Task> getByIdempotencyKey(String idempotencyKey);
}
