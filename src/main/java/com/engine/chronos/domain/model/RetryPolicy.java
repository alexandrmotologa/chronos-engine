package com.engine.chronos.domain.model;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public record RetryPolicy(
        int maxAttempts,
        Duration initialInterval,
        double multiplier,
        double jitterFactor
) implements Serializable {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, received: " + maxAttempts);
        }
        Objects.requireNonNull(initialInterval, "initialInterval must not be null");
        if (initialInterval.isNegative() || initialInterval.isZero()) {
            throw new IllegalArgumentException("initialInterval must be strictly positive");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be at least 1.0, received: " + multiplier);
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0, received: " + jitterFactor);
        }
    }

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, Duration.ofSeconds(1), 2.0, 0.2);
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, Duration.ofSeconds(1), 1.0, 0.0);
    }

    public Duration calculateBackoff(int attempt) {
        if (attempt <= 0) {
            return initialInterval;
        }

        double baseMs = initialInterval.toMillis() * Math.pow(multiplier, attempt - 1);
        if (jitterFactor <= 0.0) {
            return Duration.ofMillis((long) baseMs);
        }

        double minJitter = 1.0 - jitterFactor;
        double maxJitter = 1.0 + jitterFactor;
        double jitterMultiplier = ThreadLocalRandom.current().nextDouble(minJitter, maxJitter);
        long computedMs = Math.max(1L, (long) (baseMs * jitterMultiplier));
        return Duration.ofMillis(computedMs);
    }
}
