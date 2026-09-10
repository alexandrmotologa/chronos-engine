package com.engine.chronos.domain.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record TaskPayload(
        String target,
        Map<String, String> headers,
        String body
) implements Serializable {

    public TaskPayload {
        Objects.requireNonNull(target, "target must not be null");
        if (target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank");
        }
        headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(headers);
        body = body == null ? "" : body;
    }

    public static TaskPayload of(String target, String body) {
        return new TaskPayload(target, Collections.emptyMap(), body);
    }

    public static TaskPayload of(String target, Map<String, String> headers, String body) {
        return new TaskPayload(target, headers, body);
    }
}
