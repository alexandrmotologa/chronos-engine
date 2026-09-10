package com.engine.chronos.infrastructure.adapter.in.scheduler;

import com.engine.chronos.domain.event.DomainEvent;
import com.engine.chronos.domain.model.Task;
import com.engine.chronos.domain.model.TaskId;
import com.engine.chronos.domain.model.TaskStatus;
import com.engine.chronos.domain.port.out.DispatchResult;
import com.engine.chronos.domain.port.out.DistributedLeasePort;
import com.engine.chronos.domain.port.out.DomainEventPublisherPort;
import com.engine.chronos.domain.port.out.TaskDispatcherPort;
import com.engine.chronos.domain.port.out.TaskRepositoryPort;
import com.engine.chronos.domain.timer.HashedWheelTimer;
import com.engine.chronos.infrastructure.adapter.out.persistence.SpringDataTaskExecutionRepository;
import com.engine.chronos.infrastructure.adapter.out.persistence.TaskExecutionJpaEntity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "chronos.worker.enabled", havingValue = "true", matchIfMissing = true)
public class PartitionWorker {

    private static final Logger log = LoggerFactory.getLogger(PartitionWorker.class);

    private final String nodeId;
    private final long leaseDurationMs;
    private final int batchSize;
    private final int lookaheadWindowSeconds;

    private final TaskRepositoryPort taskRepository;
    private final DistributedLeasePort leasePort;
    private final TaskDispatcherPort taskDispatcher;
    private final SpringDataTaskExecutionRepository executionRepository;
    private final DomainEventPublisherPort eventPublisher;

    private final HashedWheelTimer wheelTimer;
    private final Set<TaskId> scheduledInTimer = ConcurrentHashMap.newKeySet();

    public PartitionWorker(
            @Value("${chronos.cluster.node-id:worker-node-1}") String nodeId,
            @Value("${chronos.cluster.lease-duration-ms:10000}") long leaseDurationMs,
            @Value("${chronos.worker.batch-size:200}") int batchSize,
            @Value("${chronos.worker.lookahead-window-seconds:30}") int lookaheadWindowSeconds,
            @Value("${chronos.timer.tick-duration-ms:100}") long tickDurationMs,
            @Value("${chronos.timer.wheel-size:512}") int wheelSize,
            TaskRepositoryPort taskRepository,
            DistributedLeasePort leasePort,
            TaskDispatcherPort taskDispatcher,
            SpringDataTaskExecutionRepository executionRepository,
            DomainEventPublisherPort eventPublisher
    ) {
        this.nodeId = nodeId;
        this.leaseDurationMs = leaseDurationMs;
        this.batchSize = batchSize;
        this.lookaheadWindowSeconds = lookaheadWindowSeconds;
        this.taskRepository = taskRepository;
        this.leasePort = leasePort;
        this.taskDispatcher = taskDispatcher;
        this.executionRepository = executionRepository;
        this.eventPublisher = eventPublisher;
        this.wheelTimer = new HashedWheelTimer(tickDurationMs, wheelSize);
    }

    @PostConstruct
    public void init() {
        wheelTimer.start();
        log.info("Started PartitionWorker {} with in-memory HashedWheelTimer", nodeId);
    }

    @PreDestroy
    public void shutdown() {
        wheelTimer.close();
        for (Integer partition : leasePort.getOwnedPartitions(nodeId)) {
            leasePort.releasePartition(partition, nodeId);
        }
        log.info("Shutdown PartitionWorker {}", nodeId);
    }

    @Scheduled(fixedDelayString = "${chronos.worker.poll-interval-ms:1000}")
    public void pollAndScheduleDueTasks() {
        try {
            Instant now = Instant.now();
            Instant cutoff = now.plusSeconds(lookaheadWindowSeconds);

            // Fetch upcoming tasks due within lookahead window
            List<Task> upcoming = taskRepository.findUpcomingTasks(now.minusSeconds(10), cutoff, batchSize);

            for (Task task : upcoming) {
                if (scheduledInTimer.contains(task.getId())) {
                    continue;
                }

                int partition = task.getPartitionBucket();
                boolean acquiredPartition = leasePort.acquirePartition(partition, nodeId, Duration.ofMillis(leaseDurationMs));
                if (!acquiredPartition) {
                    continue;
                }

                try {
                    task.acquire(nodeId, Duration.ofMillis(leaseDurationMs), now);
                    Task saved = taskRepository.save(task);
                    publishEvents(task.pollEvents());

                    scheduledInTimer.add(saved.getId());
                    wheelTimer.scheduleAt(timeout -> executeTask(saved.getId()), saved.getScheduleRule().scheduledTime());

                    log.debug("Enqueued task {} into HashedWheelTimer to fire at {}",
                            saved.getId(), saved.getScheduleRule().scheduledTime());
                } catch (Exception e) {
                    log.warn("Could not acquire lease for task {}: {}", task.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error during pollAndScheduleDueTasks cycle: {}", e.getMessage(), e);
        }
    }

    public void executeTask(TaskId taskId) {
        scheduledInTimer.remove(taskId);
        Instant now = Instant.now();

        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return;
        }

        Task task = taskOpt.get();
        if (task.getStatus() != TaskStatus.ACQUIRED) {
            log.info("Task {} status is {} (not ACQUIRED); skipping execution", taskId, task.getStatus());
            return;
        }

        try {
            task.startExecution(now);
            taskRepository.save(task);
            publishEvents(task.pollEvents());

            DispatchResult result = taskDispatcher.dispatch(task);
            Instant finishedAt = Instant.now();

            if (result.success()) {
                task.complete(result.durationMs(), finishedAt);
            } else {
                task.fail(result.errorMessage(), finishedAt);
            }

            taskRepository.save(task);
            publishEvents(task.pollEvents());

            // Record execution history
            TaskExecutionJpaEntity execution = new TaskExecutionJpaEntity(
                    UUID.randomUUID(),
                    task.getId().value(),
                    task.getRetryCount(),
                    result.success() ? "SUCCESS" : "FAILED",
                    result.durationMs(),
                    result.statusCode(),
                    result.responseBody(),
                    result.errorMessage(),
                    finishedAt
            );
            executionRepository.save(execution);

        } catch (Exception e) {
            log.error("Unhandled exception during execution of task {}: {}", taskId, e.getMessage(), e);
            try {
                task.fail(e.getMessage(), Instant.now());
                taskRepository.save(task);
            } catch (Exception ignore) {}
        }
    }

    @Scheduled(fixedDelayString = "${chronos.cluster.heartbeat-interval-ms:3000}")
    public void heartbeat() {
        try {
            List<Integer> owned = leasePort.getOwnedPartitions(nodeId);
            Duration renewal = Duration.ofMillis(leaseDurationMs);
            for (Integer partition : owned) {
                leasePort.renewPartition(partition, nodeId, renewal);
            }
        } catch (Exception e) {
            log.warn("Error during heartbeat lease renewal: {}", e.getMessage());
        }
    }

    public HashedWheelTimer getWheelTimer() {
        return wheelTimer;
    }

    private void publishEvents(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            eventPublisher.publish(event);
        }
    }
}
