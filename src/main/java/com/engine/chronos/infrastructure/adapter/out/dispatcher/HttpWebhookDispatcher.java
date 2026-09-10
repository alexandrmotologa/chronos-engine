package com.engine.chronos.infrastructure.adapter.out.dispatcher;

import com.engine.chronos.domain.model.Task;
import com.engine.chronos.domain.port.out.DispatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

@Component
public class HttpWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(HttpWebhookDispatcher.class);

    private final HttpClient httpClient;

    public HttpWebhookDispatcher() {
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @org.springframework.beans.factory.annotation.Value("${chronos.security.webhook-signing-secret:}")
    private String defaultSigningSecret;

    private final Map<String, HostCircuitState> hostCircuits = new java.util.concurrent.ConcurrentHashMap<>();

    public DispatchResult dispatch(Task task) {
        long startTime = System.currentTimeMillis();
        String targetUrl = task.getPayload().target();

        try {
            URI uri = URI.create(targetUrl);
            String host = uri.getHost();

            // Check host circuit state
            if (host != null) {
                HostCircuitState circuit = hostCircuits.computeIfAbsent(host, k -> new HostCircuitState());
                if (circuit.isCircuitOpen(startTime)) {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    log.warn("Circuit open for host {}; shedding load for task {}", host, task.getId());
                    return DispatchResult.failure(429, "Adaptive backpressure: circuit open for host " + host, durationMs);
                }
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Chronos-Engine/1.0");

            for (Map.Entry<String, String> header : task.getPayload().headers().entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            // W3C TraceContext injection
            String existingTrace = task.getPayload().headers().get(com.engine.chronos.infrastructure.config.W3CTraceContext.TRACEPARENT_HEADER);
            String traceparent = com.engine.chronos.infrastructure.config.W3CTraceContext.createChildTraceparent(existingTrace);
            requestBuilder.header(com.engine.chronos.infrastructure.config.W3CTraceContext.TRACEPARENT_HEADER, traceparent);

            // Webhook HMAC signature injection
            String signingSecret = task.getPayload().headers().getOrDefault("X-Chronos-Secret", defaultSigningSecret);
            if (signingSecret != null && !signingSecret.isBlank()) {
                long nowSec = System.currentTimeMillis() / 1000L;
                String signatureHeader = com.engine.chronos.infrastructure.adapter.out.dispatcher.security.HmacSigner.createHeader(
                        signingSecret,
                        task.getPayload().body(),
                        nowSec
                );
                requestBuilder.header("X-Chronos-Signature", signatureHeader);
            }

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(task.getPayload().body()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int durationMs = (int) (System.currentTimeMillis() - startTime);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (host != null) {
                    hostCircuits.get(host).recordSuccess();
                }
                log.info("Webhook dispatch succeeded for task {} to {} (status: {}, duration: {}ms)",
                        task.getId(), targetUrl, response.statusCode(), durationMs);
                return DispatchResult.success(response.statusCode(), response.body(), durationMs);
            } else {
                if (host != null && (response.statusCode() == 429 || response.statusCode() >= 500)) {
                    hostCircuits.get(host).recordFailure();
                }
                log.warn("Webhook dispatch received non-2xx status {} for task {} from {}",
                        response.statusCode(), task.getId(), targetUrl);
                return DispatchResult.failure(response.statusCode(), "HTTP " + response.statusCode() + ": " + response.body(), durationMs);
            }
        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            log.error("Webhook dispatch failed for task {} to {}: {}", task.getId(), targetUrl, e.getMessage());
            return DispatchResult.failure("Execution failed: " + e.getMessage(), durationMs);
        }
    }

    private static final class HostCircuitState {
        private final java.util.concurrent.atomic.AtomicInteger consecutiveFailures = new java.util.concurrent.atomic.AtomicInteger(0);
        private volatile long circuitOpenUntilMs = 0;

        boolean isCircuitOpen(long nowMs) {
            return nowMs < circuitOpenUntilMs;
        }

        void recordSuccess() {
            consecutiveFailures.set(0);
            circuitOpenUntilMs = 0;
        }

        void recordFailure() {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= 5) {
                circuitOpenUntilMs = System.currentTimeMillis() + 10000L; // 10s cooldown
            }
        }
    }
}
