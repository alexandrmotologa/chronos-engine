package com.engine.chronos.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class TaskId implements Comparable<TaskId>, Serializable {

    private final UUID value;

    private TaskId(UUID value) {
        this.value = Objects.requireNonNull(value, "TaskId value must not be null");
    }

    public static TaskId generate() {
        return new TaskId(UUID.randomUUID());
    }

    public static TaskId of(UUID uuid) {
        return new TaskId(uuid);
    }

    public static TaskId fromString(String uuidString) {
        return new TaskId(UUID.fromString(uuidString));
    }

    public UUID value() {
        return value;
    }

    @Override
    public int compareTo(TaskId other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskId taskId = (TaskId) o;
        return Objects.equals(value, taskId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
