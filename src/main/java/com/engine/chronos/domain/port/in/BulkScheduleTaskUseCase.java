package com.engine.chronos.domain.port.in;

import com.engine.chronos.domain.model.TaskId;
import java.util.List;

public interface BulkScheduleTaskUseCase {
    List<TaskId> scheduleBulk(List<ScheduleTaskCommand> commands);
}
