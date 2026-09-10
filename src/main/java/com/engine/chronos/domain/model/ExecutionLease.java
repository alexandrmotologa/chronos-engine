package com.engine.chronos.domain.model;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ExecutionLease(
        String nodeOwner,
        Instant acquiredAt,
        Instant expiresAt
) implements Serializable {

    public ExecutionLease {
        Objects.requireNonNull(nodeOwner, "nodeOwner must not be null");
        Objects.requireNonNull(acquiredAt, "acquiredAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (expiresAt.isBefore(acquiredAt)) {
            throw new IllegalArgumentException("expiresAt cannot be before acquiredAt");
        }
    }

    public static ExecutionLease of(String nodeOwner, Duration duration, Instant now) {
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new ExecutionLease(nodeOwner, now, now.plus(duration));
    }

    public ExecutionLease renew(Duration duration, Instant now) {
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new ExecutionLease(this.nodeOwner, this.acquiredAt, now.plus(duration));
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return now.isAfter(expiresAt);
    }
}
