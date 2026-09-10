package com.engine.chronos.domain.exception;

import com.engine.chronos.domain.model.TaskId;

public class TaskNotFoundException extends DomainException {

    public TaskNotFoundException(TaskId taskId) {
        super("Task not found with ID: " + taskId);
    }

    public TaskNotFoundException(String message) {
        super(message);
    }
}
