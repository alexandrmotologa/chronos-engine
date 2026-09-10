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

    public DispatchResult dispatch(Task task) {
        long startTime = System.currentTimeMillis();
        String targetUrl = task.getPayload().target();

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Chronos-Engine/1.0");

            for (Map.Entry<String, String> header : task.getPayload().headers().entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(task.getPayload().body()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int durationMs = (int) (System.currentTimeMillis() - startTime);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Webhook dispatch succeeded for task {} to {} (status: {}, duration: {}ms)",
                        task.getId(), targetUrl, response.statusCode(), durationMs);
                return DispatchResult.success(response.statusCode(), response.body(), durationMs);
            } else {
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
}
