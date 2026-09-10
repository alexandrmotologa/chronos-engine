package com.engine.chronos.application.dto;

import com.engine.chronos.domain.model.RetryPolicy;

import java.time.Duration;

public record RetryPolicyConfig(
        Integer maxAttempts,
        Long initialIntervalMs,
        Double multiplier,
        Double jitterFactor
) {
    public RetryPolicy toDomain() {
        int attempts = maxAttempts != null ? maxAttempts : 3;
        long interval = initialIntervalMs != null ? initialIntervalMs : 1000L;
        double mult = multiplier != null ? multiplier : 2.0;
        double jitter = jitterFactor != null ? jitterFactor : 0.2;

        return new RetryPolicy(attempts, Duration.ofMillis(interval), mult, jitter);
    }
}
