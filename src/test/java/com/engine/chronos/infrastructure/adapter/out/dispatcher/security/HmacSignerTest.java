package com.engine.chronos.infrastructure.adapter.out.dispatcher.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignerTest {

    private final String secret = "sec_test_k93847291038475";
    private final String payload = "{\"orderId\": 9921, \"status\": \"EXPIRED\"}";

    @Test
    @DisplayName("HmacSigner generates valid signature header and verifies successfully")
    void shouldSignAndVerify() {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        String header = HmacSigner.createHeader(secret, payload, nowSeconds);

        assertThat(header).startsWith("t=" + nowSeconds + ",v1=");

        boolean valid = HmacSigner.verify(secret, payload, header, 300);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("HmacSigner rejects tampered payload")
    void shouldRejectTamperedPayload() {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        String header = HmacSigner.createHeader(secret, payload, nowSeconds);

        String tamperedPayload = "{\"orderId\": 9921, \"status\": \"PAID\"}";
        boolean valid = HmacSigner.verify(secret, tamperedPayload, header, 300);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("HmacSigner rejects expired timestamps outside tolerance window")
    void shouldRejectExpiredTimestamp() {
        long expiredSeconds = (System.currentTimeMillis() / 1000L) - 600; // 10 minutes ago
        String header = HmacSigner.createHeader(secret, payload, expiredSeconds);

        boolean valid = HmacSigner.verify(secret, payload, header, 300); // 5 min tolerance
        assertThat(valid).isFalse();
    }
}
