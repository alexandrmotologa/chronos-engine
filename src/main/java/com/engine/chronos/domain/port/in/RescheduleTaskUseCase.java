package com.engine.chronos.domain.port.in;

import com.engine.chronos.domain.model.TaskId;
import java.time.Instant;

public interface RescheduleTaskUseCase {
    void reschedule(TaskId taskId, Instant newScheduledTime);
}
