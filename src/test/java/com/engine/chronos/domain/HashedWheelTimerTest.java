package com.engine.chronos.domain;

import com.engine.chronos.domain.timer.HashedWheelTimer;
import com.engine.chronos.domain.timer.Timeout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HashedWheelTimerTest {

    @Test
    @DisplayName("HashedWheelTimer executes task after specified delay")
    void shouldExecuteTaskAfterDelay() throws Exception {
        try (HashedWheelTimer timer = new HashedWheelTimer(50, 64)) {
            CountDownLatch latch = new CountDownLatch(1);
            long startMs = System.currentTimeMillis();
            AtomicBoolean executed = new AtomicBoolean(false);

            timer.newTimeout(timeout -> {
                executed.set(true);
                latch.countDown();
            }, Duration.ofMillis(150));

            boolean completed = latch.await(1, TimeUnit.SECONDS);
            long durationMs = System.currentTimeMillis() - startMs;

            assertThat(completed).isTrue();
            assertThat(executed.get()).isTrue();
            assertThat(durationMs).isGreaterThanOrEqualTo(140);
        }
    }

    @Test
    @DisplayName("Cancelled task is not executed by the timer")
    void shouldNotExecuteCancelledTask() throws Exception {
        try (HashedWheelTimer timer = new HashedWheelTimer(50, 64)) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean executed = new AtomicBoolean(false);

            Timeout timeout = timer.newTimeout(t -> {
                executed.set(true);
                latch.countDown();
            }, Duration.ofMillis(200));

            Thread.sleep(50);
            boolean cancelled = timeout.cancel();
            assertThat(cancelled).isTrue();
            assertThat(timeout.isCancelled()).isTrue();

            boolean ran = latch.await(400, TimeUnit.MILLISECONDS);
            assertThat(ran).isFalse();
            assertThat(executed.get()).isFalse();
        }
    }

    @Test
    @DisplayName("Timer handles multiple concurrent scheduled tasks accurately")
    void shouldHandleConcurrentTasks() throws Exception {
        int taskCount = 50;
        try (HashedWheelTimer timer = new HashedWheelTimer(20, 64)) {
            CountDownLatch latch = new CountDownLatch(taskCount);
            AtomicInteger executedCount = new AtomicInteger(0);

            for (int i = 0; i < taskCount; i++) {
                int delayMs = 50 + (i % 5) * 20;
                timer.newTimeout(timeout -> {
                    executedCount.incrementAndGet();
                    latch.countDown();
                }, Duration.ofMillis(delayMs));
            }

            boolean completed = latch.await(3, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(executedCount.get()).isEqualTo(taskCount);
            assertThat(timer.getAverageDriftMs()).isLessThan(100.0);
        }
    }
}
