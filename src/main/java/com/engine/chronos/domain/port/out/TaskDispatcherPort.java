package com.engine.chronos.domain.port.out;

import com.engine.chronos.domain.model.Task;

public interface TaskDispatcherPort {
    DispatchResult dispatch(Task task);
}
