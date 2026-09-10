package com.engine.chronos.domain.model;

public enum TaskStatus {
    SCHEDULED,
    ACQUIRED,
    EXECUTING,
    COMPLETED,
    RETRY_PENDING,
    DEAD_LETTER,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == DEAD_LETTER || this == CANCELLED;
    }

    public boolean isEligibleForAcquisition() {
        return this == SCHEDULED || this == RETRY_PENDING;
    }

    public boolean isCancellable() {
        return this == SCHEDULED || this == RETRY_PENDING;
    }
}
