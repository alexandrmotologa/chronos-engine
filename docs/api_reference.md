# API Reference

Chronos Engine exposes a RESTful interface over HTTP. Errors use the RFC 7807 Problem Details standard.

## Base URL

```
http://localhost:8080/api/v1
```

## Endpoints

### 1. Schedule task

```http
POST /api/v1/tasks
```

Creates a new one-shot or recurring scheduled task.

#### Headers
| Header | Value | Required |
|---|---|---|
| `Content-Type` | `application/json` | Yes |
| `Idempotency-Key` | String (UUID or unique business token) | Optional |

#### Request body
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
  "idempotencyKey": "order-cancel-45819",
  "status": "SCHEDULED",
  "scheduledTime": "2026-09-10T14:30:00Z",
  "type": "WEBHOOK",
  "target": "https://api.merchant.com/v1/orders/45819/expire",
  "retryCount": 0,
  "maxAttempts": 3,
  "createdAt": "2026-09-10T12:00:00Z"
}
```

### 2. Inspect task

```http
GET /api/v1/tasks/{id}
```

Returns current task state, lease details, and previous execution attempts.

#### Response: 200 OK
```json
{
  "id": "7b88dbf2-b7b5-4b36-a365-d05ec88a0321",
  "status": "COMPLETED",
  "scheduledTime": "2026-09-10T14:30:00Z",
  "type": "WEBHOOK",
  "target": "https://api.merchant.com/v1/orders/45819/expire",
  "retryCount": 1,
  "executions": [
    {
      "attemptNumber": 1,
      "status": "FAILED",
      "statusCode": 503,
      "durationMs": 142,
      "errorMessage": "Service Unavailable",
      "executedAt": "2026-09-10T14:30:00.120Z"
    },
    {
      "attemptNumber": 2,
      "status": "SUCCESS",
      "statusCode": 200,
      "durationMs": 48,
      "errorMessage": null,
      "executedAt": "2026-09-10T14:30:02.340Z"
    }
  ]
}
```

### 3. Cancel task

```http
DELETE /api/v1/tasks/{id}
```

Cancels a pending task. You can only cancel tasks in `SCHEDULED` or `RETRY_PENDING` state.

#### Response: 204 No Content

### 4. Force immediate execution

```http
POST /api/v1/tasks/{id}/fire-now
```

Triggers execution immediately, bypassing the remaining wait window.

#### Response: 202 Accepted

### 5. Dead letter queue inspection

```http
GET /api/v1/dlq?page=0&size=20
```

Returns paginated tasks that failed after exceeding maximum retry attempts.

### 6. Redrive dead letter task

```http
POST /api/v1/dlq/{id}/redrive
```

Resets a `DEAD_LETTER` task back to `SCHEDULED` with retry count zeroed.

#### Response: 200 OK

### 7. Real-time task stream

```http
GET /api/v1/tasks/live
```

Accepts `text/event-stream`. Streams live task transitions (`scheduled`, `acquired`, `executed`, `failed`, `completed`) to connected clients.
