package com.engine.chronos.infrastructure.adapter.out.dispatcher;

import com.engine.chronos.domain.model.Task;
import com.engine.chronos.domain.model.TaskType;
import com.engine.chronos.domain.port.out.DispatchResult;
import com.engine.chronos.domain.port.out.TaskDispatcherPort;
import org.springframework.stereotype.Component;

@Component
public class RoutingTaskDispatcher implements TaskDispatcherPort {

    private final HttpWebhookDispatcher webhookDispatcher;
    private final KafkaMessageDispatcher kafkaDispatcher;

    public RoutingTaskDispatcher(
            HttpWebhookDispatcher webhookDispatcher,
            KafkaMessageDispatcher kafkaDispatcher
    ) {
        this.webhookDispatcher = webhookDispatcher;
        this.kafkaDispatcher = kafkaDispatcher;
    }

    @Override
    public DispatchResult dispatch(Task task) {
        if (task.getType() == TaskType.KAFKA) {
            return kafkaDispatcher.dispatch(task);
        } else {
            return webhookDispatcher.dispatch(task);
        }
    }
}
