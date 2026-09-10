package com.engine.chronos.domain.port.in;

import com.engine.chronos.domain.model.TaskId;

public interface ScheduleTaskUseCase {
    TaskId schedule(ScheduleTaskCommand command);
}
