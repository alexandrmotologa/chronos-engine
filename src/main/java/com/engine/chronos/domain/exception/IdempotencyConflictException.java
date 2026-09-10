package com.engine.chronos.domain.exception;

public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("A task with idempotency key already exists: " + idempotencyKey);
    }
}
