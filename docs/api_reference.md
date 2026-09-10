# API Reference

Chronos Engine exposes a RESTful interface over HTTP. Errors use the RFC 7807 Problem Details standard.

## Base URL

```
http://localhost:8080/api/v1
```

## Endpoints

### 1. Schedule Task

```http
POST /api/v1/tasks
```

Creates a new one-shot or recurring scheduled task.

#### Headers
| Header | Value | Required |
|---|---|---|
| `Content-Type` | `application/json` | Yes |
| `Idempotency-Key` | String (UUID or unique business token) | Optional |

#### Request Body
```json
{
  "idempotencyKey": "order-cancel-45819",
  "scheduledTime": "2026-09-10T14:30:00Z",
  "type": "WEBHOOK",
  "target": "https://api.merchant.com/v1/orders/45819/expire",
  "headers": {
    "Authorization": "Bearer token-value",
    "X-Request-Source": "chronos"
  },
  "payload": "{\"orderId\": 45819}",
  "tags": ["orders", "billing"],
  "retryPolicy": {
    "maxAttempts": 3,
    "initialIntervalMs": 1000,
    "multiplier": 2.0,
    "jitterFactor": 0.2
  }
}
```

#### Response: 201 Created
```json
{
  "id": "7b88dbf2-b7b5-4b36-a365-d05ec88a0321",
  "status": "SCHEDULED",
  "location": "/api/v1/tasks/7b88dbf2-b7b5-4b36-a365-d05ec88a0321"
}
```

---

### 2. Bulk Schedule Tasks

```http
POST /api/v1/tasks/bulk
```

Schedules a batch of tasks in a single database transaction.

#### Request Body
```json
{
  "tasks": [
    {
      "target": "https://api.merchant.com/v1/orders/1/expire",
      "scheduledTime": "2026-09-10T14:30:00Z",
      "payload": "{\"orderId\": 1}",
      "tags": ["batch-job", "orders"]
    },
    {
      "target": "https://api.merchant.com/v1/orders/2/expire",
      "scheduledTime": "2026-09-10T14:30:05Z",
      "payload": "{\"orderId\": 2}",
      "tags": ["batch-job", "orders"]
    }
  ]
}
```

#### Response: 200 OK
```json
{
  "total": 2,
  "scheduled": 2,
  "taskIds": [
    "7b88dbf2-b7b5-4b36-a365-d05ec88a0321",
    "9c12fe34-c8d7-4e12-b918-a12cd34ef567"
  ],
  "errors": []
}
```

---

### 3. Inspect Task

```http
GET /api/v1/tasks/{id}
```

Returns current task state, lease details, tags, and previous execution attempts.

#### Response: 200 OK
```json
{
  "id": "7b88dbf2-b7b5-4b36-a365-d05ec88a0321",
  "idempotencyKey": "order-cancel-45819",
  "partitionBucket": 42,
  "type": "WEBHOOK",
  "status": "COMPLETED",
  "target": "https://api.merchant.com/v1/orders/45819/expire",
  "scheduledTime": "2026-09-10T14:30:00Z",
  "retryCount": 1,
  "maxAttempts": 3,
  "leaseOwner": null,
  "leaseExpiresAt": null,
  "tags": ["orders", "billing"],
  "createdAt": "2026-09-10T12:00:00Z",
  "updatedAt": "2026-09-10T14:30:02Z",
  "executions": [
    {
      "attemptNumber": 1,
      "status": "FAILED",
      "durationMs": 142,
      "statusCode": 503,
      "errorMessage": "Service Unavailable",
      "executedAt": "2026-09-10T14:30:00.120Z"
    },
    {
      "attemptNumber": 2,
      "status": "SUCCESS",
      "durationMs": 48,
      "statusCode": 200,
      "errorMessage": null,
      "executedAt": "2026-09-10T14:30:02.340Z"
    }
  ]
}
```

---

### 4. Reschedule Task

```http
POST /api/v1/tasks/{id}/reschedule
```

Updates the target scheduled execution time for an uncompleted task.

#### Request Body
```json
{
  "newScheduledTime": "2026-09-10T16:00:00Z"
}
```

#### Response: 200 OK

---

### 5. Pause Task

```http
POST /api/v1/tasks/{id}/pause
```

Transitions a scheduled task into PAUSED state, preventing execution until resumed.

#### Response: 200 OK

---

### 6. Resume Task

```http
POST /api/v1/tasks/{id}/resume
```

Transitions a paused task back into SCHEDULED state.

#### Response: 200 OK

---

### 7. Trigger Immediate Execution (Fire Now)

```http
POST /api/v1/tasks/{id}/fire-now
```

Forces immediate execution without waiting for the scheduled timestamp.

#### Response: 202 Accepted

---

### 8. Cancel Task

```http
DELETE /api/v1/tasks/{id}
```

Cancels an active scheduled or paused task.

#### Response: 204 No Content

---

### 9. Query Tasks by Tag

```http
GET /api/v1/tasks?tag={tagName}
```

Returns all tasks matching the specified tag.

#### Response: 200 OK
```json
[
  {
    "id": "7b88dbf2-b7b5-4b36-a365-d05ec88a0321",
    "status": "SCHEDULED",
    "target": "https://api.merchant.com/v1/orders/1/expire",
    "tags": ["batch-job", "orders"]
  }
]
```

---

### 10. Cancel Tasks by Tag

```http
DELETE /api/v1/tasks?tag={tagName}
```

Cancels all eligible active tasks matching the specified tag.

#### Response: 200 OK
```json
{
  "tag": "batch-job",
  "cancelledCount": 12
}
```

---

### 11. Timeline Horizon Query

```http
GET /api/v1/tasks/timeline?windowSec=60
```

Returns tasks scheduled within the upcoming window (default 60 seconds) for Gantt visualizations.

---

### 12. Dead Letter Queue Endpoints

#### List Dead Letters
```http
GET /api/v1/dlq?page=0&size=20
```

#### Redrive Dead Letter Task
```http
POST /api/v1/dlq/{id}/redrive
```
Request Body:
```json
{
  "scheduledTime": "2026-09-10T15:00:00Z"
}
```

---

## Webhook Signatures & Distributed Tracing

### HMAC Webhook Signatures
When `chronos.dispatcher.http.signing-secret` is configured, outgoing webhooks include cryptographic verification headers:

- `X-Chronos-Timestamp`: Unix epoch timestamp in milliseconds.
- `X-Chronos-Signature`: Signature in format `t={timestamp},v1={hex_hmac_sha256}` computed over `{timestamp}.{payload}`.

### W3C Distributed Tracing
Every dispatched request injects standard W3C trace context headers:
- `traceparent`: Format `00-{trace_id}-{span_id}-01`. Allows distributed tracing systems (OpenTelemetry, Datadog, Jaeger) to correlate delayed task execution with upstream initiator traces.
