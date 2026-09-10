package com.engine.chronos.domain.model;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ScheduleRule(
        Instant scheduledTime,
        String cronExpression
) implements Serializable {

    public ScheduleRule {
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
    }

    public static ScheduleRule at(Instant targetTime) {
        return new ScheduleRule(targetTime, null);
    }

    public static ScheduleRule in(Duration delay, Instant now) {
        Objects.requireNonNull(delay, "delay must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new ScheduleRule(now.plus(delay), null);
    }

    public static ScheduleRule cron(Instant initialTime, String cronExpression) {
        Objects.requireNonNull(cronExpression, "cronExpression must not be null");
        return new ScheduleRule(initialTime, cronExpression);
    }

    public Optional<String> getCronExpression() {
        return Optional.ofNullable(cronExpression);
    }

    public boolean isRecurring() {
        return cronExpression != null && !cronExpression.isBlank();
    }
}
