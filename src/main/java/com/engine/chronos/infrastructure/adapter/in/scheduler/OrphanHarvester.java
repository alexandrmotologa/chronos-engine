package com.engine.chronos.infrastructure.adapter.in.scheduler;

import com.engine.chronos.domain.port.out.TaskRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OrphanHarvester {

    private static final Logger log = LoggerFactory.getLogger(OrphanHarvester.class);

    private final TaskRepositoryPort taskRepository;

    public OrphanHarvester(TaskRepositoryPort taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Scheduled(fixedDelayString = "${chronos.cluster.orphan-harvest-interval-ms:5000}")
    public void harvestOrphans() {
        try {
            Instant now = Instant.now();
            int reclaimed = taskRepository.resetExpiredLeases(now);
            if (reclaimed > 0) {
                log.warn("Orphan harvester recovered {} expired task leases back to SCHEDULED", reclaimed);
            }
        } catch (Exception e) {
            log.error("Failed during orphan harvest cycle: {}", e.getMessage(), e);
        }
    }
}
