package com.engine.chronos.domain.port.out;

import java.io.Serializable;

public record DispatchResult(
        boolean success,
        int statusCode,
        String responseBody,
        String errorMessage,
        int durationMs
) implements Serializable {

    public static DispatchResult success(int statusCode, String responseBody, int durationMs) {
        return new DispatchResult(true, statusCode, responseBody, null, durationMs);
    }

    public static DispatchResult failure(int statusCode, String errorMessage, int durationMs) {
        return new DispatchResult(false, statusCode, null, errorMessage, durationMs);
    }

    public static DispatchResult failure(String errorMessage, int durationMs) {
        return new DispatchResult(false, 0, null, errorMessage, durationMs);
    }
}
