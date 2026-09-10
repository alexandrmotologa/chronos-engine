package com.engine.chronos.infrastructure.config;

import com.engine.chronos.domain.event.*;
import com.engine.chronos.infrastructure.adapter.in.scheduler.PartitionWorker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ChronosMetrics {

    private final Counter scheduledCounter;
    private final Counter executedCounter;
    private final Counter failedCounter;
    private final Counter deadLetterCounter;
    private final Timer executionLatencyTimer;

    public ChronosMetrics(MeterRegistry registry, @Autowired(required = false) PartitionWorker partitionWorker) {
        this.scheduledCounter = Counter.builder("chronos.tasks.scheduled.total")
                .description("Total number of tasks scheduled")
                .register(registry);

        this.executedCounter = Counter.builder("chronos.tasks.fired.total")
                .description("Total number of tasks successfully executed")
                .register(registry);

        this.failedCounter = Counter.builder("chronos.tasks.failed.total")
                .description("Total number of task execution failures")
                .register(registry);

        this.deadLetterCounter = Counter.builder("chronos.tasks.deadletter.total")
                .description("Total number of tasks moved to dead letter queue")
                .register(registry);

        this.executionLatencyTimer = Timer.builder("chronos.execution.latency")
                .description("Execution latency for task callbacks")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        if (partitionWorker != null) {
            Gauge.builder("chronos.timer.pending", partitionWorker.getWheelTimer(), t -> (double) t.getPendingTimeouts())
                    .description("Current number of active pending timeouts in HashedWheelTimer")
                    .register(registry);

            Gauge.builder("chronos.timer.drift.ms", partitionWorker.getWheelTimer(), t -> t.getAverageDriftMs())
                    .description("Average drift in milliseconds between target schedule and actual tick")
                    .register(registry);
        }
    }

    @EventListener
    public void onTaskScheduled(TaskScheduledEvent event) {
        scheduledCounter.increment();
    }

    @EventListener
    public void onTaskExecuted(TaskExecutedEvent event) {
        executedCounter.increment();
        executionLatencyTimer.record(event.durationMs(), TimeUnit.MILLISECONDS);
    }

    @EventListener
    public void onTaskFailed(TaskFailedEvent event) {
        failedCounter.increment();
    }

    @EventListener
    public void onTaskDeadLettered(TaskDeadLetteredEvent event) {
        deadLetterCounter.increment();
    }
}
