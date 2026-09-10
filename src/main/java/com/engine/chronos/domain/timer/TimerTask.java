package com.engine.chronos.domain.timer;

@FunctionalInterface
public interface TimerTask {
    void run(Timeout timeout) throws Exception;
}
