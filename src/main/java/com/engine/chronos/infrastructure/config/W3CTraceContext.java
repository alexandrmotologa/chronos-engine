package com.engine.chronos.infrastructure.config;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class W3CTraceContext {

    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACESTATE_HEADER = "tracestate";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private W3CTraceContext() {}

    public static String generateTraceparent() {
        byte[] traceId = new byte[16];
        byte[] spanId = new byte[8];
        RANDOM.nextBytes(traceId);
        RANDOM.nextBytes(spanId);

        return "00-" + HEX.formatHex(traceId) + "-" + HEX.formatHex(spanId) + "-01";
    }

    public static String createChildTraceparent(String parentTraceparent) {
        if (parentTraceparent == null || parentTraceparent.isBlank()) {
            return generateTraceparent();
        }

        String[] parts = parentTraceparent.split("-");
        if (parts.length < 4 || parts[1].length() != 32) {
            return generateTraceparent();
        }

        String traceId = parts[1];
        byte[] newSpanId = new byte[8];
        RANDOM.nextBytes(newSpanId);
        String flags = parts[3];

        return "00-" + traceId + "-" + HEX.formatHex(newSpanId) + "-" + flags;
    }

    public static String extractTraceId(String traceparent) {
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }
}
