package com.engine.chronos.infrastructure.adapter.in.rest;

import com.engine.chronos.domain.event.DomainEvent;
import com.engine.chronos.domain.port.out.DistributedLeasePort;
import com.engine.chronos.domain.port.out.TaskRepositoryPort;
import com.engine.chronos.infrastructure.adapter.in.scheduler.PartitionWorker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Observability", description = "Server-Sent Events and cluster health endpoints")
public class LiveEventStreamController {

    private static final Logger log = LoggerFactory.getLogger(LiveEventStreamController.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final String nodeId;
    private final PartitionWorker partitionWorker;
    private final DistributedLeasePort leasePort;
    private final TaskRepositoryPort taskRepository;

    public LiveEventStreamController(
            @Value("${chronos.cluster.node-id:node-1}") String nodeId,
            @Autowired(required = false) PartitionWorker partitionWorker,
            DistributedLeasePort leasePort,
            TaskRepositoryPort taskRepository
    ) {
        this.nodeId = nodeId;
        this.partitionWorker = partitionWorker;
        this.leasePort = leasePort;
        this.taskRepository = taskRepository;
    }

    @GetMapping(value = "/tasks/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to real-time task lifecycle stream via Server-Sent Events")
    public SseEmitter subscribeToEvents() {
        SseEmitter emitter = new SseEmitter(180_000L); // 3-minute timeout with client reconnect
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of("nodeId", nodeId, "connectedAt", Instant.now().toString())));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    @GetMapping("/cluster/status")
    @Operation(summary = "Query node health, drift, and partition ownership")
    public Map<String, Object> getClusterStatus() {
        double avgDrift = 0.0;
        long pending = 0L;
        if (partitionWorker != null && partitionWorker.getWheelTimer() != null) {
            avgDrift = partitionWorker.getWheelTimer().getAverageDriftMs();
            pending = partitionWorker.getWheelTimer().getPendingTimeouts();
        }
        List<Integer> ownedPartitions = leasePort.getOwnedPartitions(nodeId);
        long deadLetters = taskRepository.countDeadLetters();

        return Map.of(
                "nodeId", nodeId,
                "status", "HEALTHY",
                "averageDriftMs", Math.round(avgDrift * 100.0) / 100.0,
                "pendingTimeouts", pending,
                "ownedPartitionsCount", ownedPartitions.size(),
                "ownedPartitions", ownedPartitions,
                "deadLettersCount", deadLetters,
                "timestamp", Instant.now().toString()
        );
    }

    @EventListener
    public void onDomainEvent(DomainEvent event) {
        Map<String, Object> payload = Map.of(
                "eventType", event.getClass().getSimpleName(),
                "taskId", event.taskId().toString(),
                "occurredAt", event.occurredAt().toString()
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("TASK_EVENT")
                        .data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
