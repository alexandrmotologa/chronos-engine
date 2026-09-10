package com.engine.chronos.infrastructure.adapter.out.dispatcher;

import com.engine.chronos.domain.model.Task;
import com.engine.chronos.domain.port.out.DispatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class KafkaMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageDispatcher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaMessageDispatcher(@Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public DispatchResult dispatch(Task task) {
        long startTime = System.currentTimeMillis();
        String topic = task.getPayload().target();

        if (kafkaTemplate == null) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            log.warn("KafkaTemplate not configured in current profile; mocking dispatch for task {} to topic {}",
                    task.getId(), topic);
            return DispatchResult.success(200, "Mock Kafka ACK", durationMs);
        }

        try {
            String key = task.getIdempotencyKey() != null ? task.getIdempotencyKey() : task.getId().toString();
            var sendResult = kafkaTemplate.send(topic, key, task.getPayload().body()).get(5, TimeUnit.SECONDS);

            int durationMs = (int) (System.currentTimeMillis() - startTime);
            log.info("Kafka dispatch succeeded for task {} to topic {} partition {} offset {}",
                    task.getId(), topic, sendResult.getRecordMetadata().partition(), sendResult.getRecordMetadata().offset());

            return DispatchResult.success(200, "Kafka offset " + sendResult.getRecordMetadata().offset(), durationMs);
        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            log.error("Kafka dispatch failed for task {} to topic {}: {}", task.getId(), topic, e.getMessage());
            return DispatchResult.failure("Kafka publish failed: " + e.getMessage(), durationMs);
        }
    }
}
