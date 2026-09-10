package com.engine.chronos.infrastructure.adapter.out.persistence;

import com.engine.chronos.domain.port.in.ScheduleTaskCommand;
import com.engine.chronos.domain.port.in.ScheduleTaskUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 100;

    private final SpringDataOutboxRepository outboxRepository;
    private final ScheduleTaskUseCase scheduleTaskUseCase;

    public OutboxPoller(
            SpringDataOutboxRepository outboxRepository,
            ScheduleTaskUseCase scheduleTaskUseCase
    ) {
        this.outboxRepository = outboxRepository;
        this.scheduleTaskUseCase = scheduleTaskUseCase;
    }

    @Scheduled(fixedDelayString = "${chronos.outbox.poll-interval-ms:500}")
    @Transactional
    public void processOutbox() {
        List<OutboxTaskJpaEntity> pending = outboxRepository.findPending(PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        int processed = 0;

        for (OutboxTaskJpaEntity item : pending) {
            try {
                ScheduleTaskCommand command = item.toCommand();
                scheduleTaskUseCase.schedule(command);
                item.markProcessed(now);
                processed++;
            } catch (Exception e) {
                log.error("Failed to process outbox task {}: {}", item.getId(), e.getMessage());
                item.markFailed(e.getMessage(), now);
            }
        }

        outboxRepository.saveAllAndFlush(pending);
        log.debug("Outbox processed {} pending task(s)", processed);
    }
}
