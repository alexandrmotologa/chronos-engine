package com.engine.chronos.domain.timer;

public interface Timeout {

    TimerTask task();

    boolean isExpired();

    boolean isCancelled();

    boolean cancel();

    long deadlineMs();
}
