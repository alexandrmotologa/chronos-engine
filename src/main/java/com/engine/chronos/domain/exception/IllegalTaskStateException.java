package com.engine.chronos.domain.exception;

import com.engine.chronos.domain.model.TaskId;
import com.engine.chronos.domain.model.TaskStatus;

public class IllegalTaskStateException extends DomainException {

    public IllegalTaskStateException(TaskId taskId, TaskStatus currentStatus, String attemptedAction) {
        super("Task %s in state %s cannot perform action: %s".formatted(taskId, currentStatus, attemptedAction));
    }

    public IllegalTaskStateException(String message) {
        super(message);
    }
}
