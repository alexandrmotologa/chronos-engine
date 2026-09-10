package com.engine.chronos.domain.timer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class HashedWheelTimer implements AutoCloseable {

    private static final int STATE_INIT = 0;
    private static final int STATE_STARTED = 1;
    private static final int STATE_SHUTDOWN = 2;

    private final AtomicInteger workerState = new AtomicInteger(STATE_INIT);
    private final CountDownLatch startTimeInitialized = new CountDownLatch(1);

    private final long tickDurationMs;
    private final int mask;
    private final HashedWheelBucket[] wheel;
    private final Queue<HashedWheelTimeout> timeouts = new ConcurrentLinkedQueue<>();
    private final Queue<HashedWheelTimeout> cancelledTimeouts = new ConcurrentLinkedQueue<>();
    private final AtomicLong pendingTimeouts = new AtomicLong(0);

    private final AtomicLong totalDriftMs = new AtomicLong(0);
    private final AtomicLong executedTimeoutsCount = new AtomicLong(0);

    private volatile long startTimeMs;
    private Thread workerThread;

    public HashedWheelTimer(long tickDurationMs, int ticksPerWheel) {
        if (tickDurationMs <= 0) {
            throw new IllegalArgumentException("tickDurationMs must be greater than 0: " + tickDurationMs);
        }
        if (ticksPerWheel <= 0) {
            throw new IllegalArgumentException("ticksPerWheel must be greater than 0: " + ticksPerWheel);
        }

        int normalizedTicks = normalizeTicksPerWheel(ticksPerWheel);
        this.tickDurationMs = tickDurationMs;
        this.mask = normalizedTicks - 1;
        this.wheel = createWheel(normalizedTicks);
    }

    public HashedWheelTimer() {
        this(100, 512);
    }

    private static int normalizeTicksPerWheel(int ticks) {
        int normalized = 1;
        while (normalized < ticks) {
            normalized <<= 1;
        }
        return normalized;
    }

    private static HashedWheelBucket[] createWheel(int ticks) {
        HashedWheelBucket[] buckets = new HashedWheelBucket[ticks];
        for (int i = 0; i < ticks; i++) {
            buckets[i] = new HashedWheelBucket();
        }
        return buckets;
    }

    public synchronized void start() {
        switch (workerState.get()) {
            case STATE_INIT -> {
                if (workerState.compareAndSet(STATE_INIT, STATE_STARTED)) {
                    workerThread = Thread.ofVirtual()
                            .name("chronos-hashed-wheel-timer")
                            .start(this::runWorker);
                }
            }
            case STATE_STARTED -> {
                // already running
            }
            case STATE_SHUTDOWN -> throw new IllegalStateException("Cannot restart a closed HashedWheelTimer");
            default -> throw new IllegalStateException("Unknown worker state: " + workerState.get());
        }

        while (startTimeMs == 0) {
            try {
                startTimeInitialized.await();
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public Timeout newTimeout(TimerTask task, Duration delay) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(delay, "delay must not be null");

        start();

        long delayMs = Math.max(0, delay.toMillis());
        long deadlineMs = System.currentTimeMillis() + delayMs;

        HashedWheelTimeout timeout = new HashedWheelTimeout(this, task, deadlineMs);
        timeouts.add(timeout);
        pendingTimeouts.incrementAndGet();
        return timeout;
    }

    public Timeout scheduleAt(TimerTask task, Instant targetTime) {
        Objects.requireNonNull(targetTime, "targetTime must not be null");
        Duration delay = Duration.between(Instant.now(), targetTime);
        return newTimeout(task, delay.isNegative() ? Duration.ZERO : delay);
    }

    private void runWorker() {
        startTimeMs = System.currentTimeMillis();
        if (startTimeMs == 0) {
            startTimeMs = 1;
        }
        startTimeInitialized.countDown();

        long tick = 0;

        while (workerState.get() == STATE_STARTED) {
            final long deadline = waitForNextTick(tick);
            if (deadline > 0) {
                int bucketIdx = (int) (tick & mask);
                processCancelledTasks();
                HashedWheelBucket bucket = wheel[bucketIdx];
                transferTimeoutsToBuckets();
                bucket.expireTimeouts(deadline);
                tick++;
            }
        }

        // Clean up remaining buckets on shutdown
        for (HashedWheelBucket bucket : wheel) {
            bucket.clearTimeouts();
        }
        processCancelledTasks();
    }

    private long waitForNextTick(long tick) {
        long deadline = startTimeMs + (tick + 1) * tickDurationMs;

        while (true) {
            final long currentTime = System.currentTimeMillis();
            long sleepTimeMs = deadline - currentTime;

            if (sleepTimeMs <= 0) {
                return currentTime;
            }

            try {
                Thread.sleep(sleepTimeMs);
            } catch (InterruptedException e) {
                if (workerState.get() == STATE_SHUTDOWN) {
                    return Long.MIN_VALUE;
                }
            }
        }
    }

    private void transferTimeoutsToBuckets() {
        // Transfer up to 100,000 timeouts per tick to prevent starvation
        for (int i = 0; i < 100000; i++) {
            HashedWheelTimeout timeout = timeouts.poll();
            if (timeout == null) {
                break;
            }
            if (timeout.isCancelled()) {
                continue;
            }

            long calculated = (timeout.deadlineMs - startTimeMs) / tickDurationMs;
            long remainingRounds = (calculated - (timeout.deadlineMs < System.currentTimeMillis() ? 0 : 0)) / wheel.length;
            long stopIndex = calculated & mask;

            timeout.remainingRounds = Math.max(0, (timeout.deadlineMs - System.currentTimeMillis()) / (tickDurationMs * wheel.length));
            HashedWheelBucket bucket = wheel[(int) stopIndex];
            bucket.addTimeout(timeout);
        }
    }

    private void processCancelledTasks() {
        while (true) {
            HashedWheelTimeout timeout = cancelledTimeouts.poll();
            if (timeout == null) {
                break;
            }
            timeout.remove();
        }
    }

    void cancelTimeout(HashedWheelTimeout timeout) {
        cancelledTimeouts.add(timeout);
        pendingTimeouts.decrementAndGet();
    }

    void recordExecution(long deadlineMs) {
        long current = System.currentTimeMillis();
        long drift = Math.max(0, current - deadlineMs);
        totalDriftMs.addAndGet(drift);
        executedTimeoutsCount.incrementAndGet();
        pendingTimeouts.decrementAndGet();
    }

    public double getAverageDriftMs() {
        long count = executedTimeoutsCount.get();
        return count == 0 ? 0.0 : (double) totalDriftMs.get() / count;
    }

    public long getPendingTimeouts() {
        return Math.max(0, pendingTimeouts.get());
    }

    @Override
    public synchronized void close() {
        if (workerState.compareAndSet(STATE_STARTED, STATE_SHUTDOWN)
                || workerState.compareAndSet(STATE_INIT, STATE_SHUTDOWN)) {
            if (workerThread != null) {
                workerThread.interrupt();
            }
        }
    }

    // Inner classes: Timeout and Bucket
    private static final class HashedWheelTimeout implements Timeout {
        private static final int ST_INIT = 0;
        private static final int ST_CANCELLED = 1;
        private static final int ST_EXPIRED = 2;

        private final HashedWheelTimer timer;
        private final TimerTask task;
        private final long deadlineMs;
        private final AtomicInteger state = new AtomicInteger(ST_INIT);

        long remainingRounds;
        HashedWheelTimeout next;
        HashedWheelTimeout prev;
        HashedWheelBucket bucket;

        HashedWheelTimeout(HashedWheelTimer timer, TimerTask task, long deadlineMs) {
            this.timer = timer;
            this.task = task;
            this.deadlineMs = deadlineMs;
        }

        @Override
        public TimerTask task() {
            return task;
        }

        @Override
        public boolean isExpired() {
            return state.get() == ST_EXPIRED;
        }

        @Override
        public boolean isCancelled() {
            return state.get() == ST_CANCELLED;
        }

        @Override
        public boolean cancel() {
            if (state.compareAndSet(ST_INIT, ST_CANCELLED)) {
                timer.cancelTimeout(this);
                return true;
            }
            return false;
        }

        @Override
        public long deadlineMs() {
            return deadlineMs;
        }

        void expire() {
            if (state.compareAndSet(ST_INIT, ST_EXPIRED)) {
                timer.recordExecution(deadlineMs);
                Thread.startVirtualThread(() -> {
                    try {
                        task.run(this);
                    } catch (Throwable t) {
                        // Task execution exception caught here to prevent thread disruption
                    }
                });
            }
        }

        void remove() {
            HashedWheelBucket b = bucket;
            if (b != null) {
                b.remove(this);
            }
        }
    }

    private static final class HashedWheelBucket {
        private HashedWheelTimeout head;
        private HashedWheelTimeout tail;

        void addTimeout(HashedWheelTimeout timeout) {
            timeout.bucket = this;
            if (head == null) {
                head = tail = timeout;
            } else {
                tail.next = timeout;
                timeout.prev = tail;
                tail = timeout;
            }
        }

        void expireTimeouts(long currentTimeMs) {
            HashedWheelTimeout timeout = head;

            while (timeout != null) {
                HashedWheelTimeout next = timeout.next;
                if (timeout.isCancelled()) {
                    remove(timeout);
                } else if (timeout.deadlineMs <= currentTimeMs) {
                    remove(timeout);
                    timeout.expire();
                } else if (timeout.remainingRounds <= 0) {
                    remove(timeout);
                    timeout.expire();
                } else {
                    timeout.remainingRounds--;
                }
                timeout = next;
            }
        }

        void remove(HashedWheelTimeout timeout) {
            HashedWheelTimeout next = timeout.next;
            if (timeout.prev != null) {
                timeout.prev.next = next;
            }
            if (timeout.next != null) {
                timeout.next.prev = timeout.prev;
            }

            if (timeout == head) {
                head = next;
            }
            if (timeout == tail) {
                tail = timeout.prev;
            }
            timeout.prev = null;
            timeout.next = null;
            timeout.bucket = null;
        }

        void clearTimeouts() {
            HashedWheelTimeout timeout = head;
            while (timeout != null) {
                HashedWheelTimeout next = timeout.next;
                remove(timeout);
                timeout = next;
            }
        }
    }
}
