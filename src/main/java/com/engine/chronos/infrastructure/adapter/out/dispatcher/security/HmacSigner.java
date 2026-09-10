package com.engine.chronos.infrastructure.adapter.out.dispatcher.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

public final class HmacSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private HmacSigner() {}

    public static String sign(String secret, String payload, long timestampSeconds) {
        Objects.requireNonNull(secret, "secret must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        String dataToSign = timestampSeconds + "." + payload;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    public static String createHeader(String secret, String payload, long timestampSeconds) {
        String signature = sign(secret, payload, timestampSeconds);
        return "t=" + timestampSeconds + ",v1=" + signature;
    }

    public static boolean verify(String secret, String payload, String header, long toleranceSeconds) {
        if (secret == null || payload == null || header == null) {
            return false;
        }

        try {
            String[] parts = header.split(",");
            Long timestamp = null;
            String signature = null;

            for (String part : parts) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    if ("t".equals(kv[0].trim())) {
                        timestamp = Long.parseLong(kv[1].trim());
                    } else if ("v1".equals(kv[0].trim())) {
                        signature = kv[1].trim();
                    }
                }
            }

            if (timestamp == null || signature == null) {
                return false;
            }

            // Check timestamp tolerance
            long currentSeconds = System.currentTimeMillis() / 1000L;
            if (toleranceSeconds > 0 && Math.abs(currentSeconds - timestamp) > toleranceSeconds) {
                return false;
            }

            String expectedSignature = sign(secret, payload, timestamp);
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
