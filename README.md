# Chronos Engine

Distributed delayed task and scheduling engine built on Java 21 Virtual Threads, an in-memory timing wheel, and PostgreSQL partition leases.

Chronos Engine executes deferred webhooks, event dispatches, and timeout actions with at-least-once durability and zero duplicate executions under cluster contention.

```
                  +-----------------------------------+
                  |         HTTP / Kafka API          |
                  +-----------------+-----------------+
                                    |
                        (Schedule / Cancel / Query)
                                    v
+-------------------------------------------------------------------------+
|                              Chronos Core                               |
|                                                                         |
|  +------------------------+                     +--------------------+  |
|  |   Hashed Wheel Timer   |                     | Partition Worker   |  |
|  |   (In-memory, O(1))    |                     | (Lease heartbeats) |  |
|  +-----------+------------+                     +---------+----------+  |
|              |                                            |             |
|              +--------------------+-----------------------+             |
|                                   |                                     |
|                       Java 21 Virtual Threads                           |
|                       (Concurrent Dispatchers)                          |
+-----------------------------------+-------------------------------------+
                                    |
             +----------------------+----------------------+
             |                                             |
             v                                             v
  +--------------------+                         +--------------------+
  | Webhook Dispatcher |                         |  Kafka Dispatcher  |
  |  (HTTP/2 client)   |                         |  (Message topics)  |
  +--------------------+                         +--------------------+
```

## Problem statement

Distributed systems often need tasks executed at an exact future time:
- Expiring checkout reservations after fifteen minutes.
- Retrying failed external API calls with exponential backoff and randomized jitter.
- Deactivating expired trial subscriptions across multi-tenant accounts.

Traditional background workers using database polling suffer from high table lock contention or frequent full table scans. In-memory tools such as Spring `@Scheduled` lose tasks on restart and run into split-brain errors when scaled horizontally.

Chronos Engine solves this by pairing an in-memory hashed wheel timer for sub-millisecond dispatch accuracy with PostgreSQL partition leases using `SELECT ... FOR UPDATE SKIP LOCKED` for durability.

## Architecture

Chronos follows strict Hexagonal Architecture (Ports and Adapters):

- `domain`: Pure Java 21 records and models. Contains zero dependencies on Spring, Hibernate, or external frameworks.
- `application`: Use case handlers, retry calculators, and orchestration logic.
- `infrastructure`: Concrete adapters for PostgreSQL persistence, virtual thread dispatchers, Kafka publishers, and REST controllers.

Read [docs/architecture.md](docs/architecture.md) for module boundaries and [docs/state_machine.md](docs/state_machine.md) for the complete task lifecycle.

## Features

- **Java 21 Virtual Threads**: Millions of concurrent delayed callbacks without OS thread exhaustion.
- **Hashed wheel timer**: O(1) task scheduling and bucket traversal.
- **Distributed partition leases**: 256 virtual buckets balanced across active cluster instances.
- **Automatic failover**: Orphan harvester reclaims tasks within ten seconds if a node crashes.
- **Idempotency keys**: Prevents duplicate scheduling on client retries.
- **Dead letter queue with redrive**: Inspect failed executions and re-queue tasks through the REST API.
- **Embedded live dashboard**: Real-time Gantt timeline and cluster status rendered via Server-Sent Events at `/dashboard`.

## Requirements

- Java 21 LTS
- Maven 3.9+
- PostgreSQL 16+
- Docker & Docker Compose (optional, for running dependencies locally)

## Quickstart

### 1. Start PostgreSQL

```bash
docker run -d \
  --name chronos-postgres \
  -e POSTGRES_DB=chronos \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine
```

### 2. Build and run the engine

```bash
mvn clean package
java -jar target/chronos-engine-0.1.0-SNAPSHOT.jar
```

Flyway executes database migrations automatically on application startup.

### 3. Schedule a delayed webhook

Schedule a webhook to fire in 30 seconds:

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "order-cancel-9821",
    "scheduledTime": "'$(date -u -d "+30 seconds" +"%Y-%m-%dT%H:%M:%SZ")'",
    "type": "WEBHOOK",
    "target": "https://api.example.com/webhooks/orders",
    "payload": "{\"orderId\": 9821, \"action\": \"EXPIRE\"}",
    "retryPolicy": {
      "maxAttempts": 3,
      "initialIntervalMs": 1000,
      "multiplier": 2.0,
      "jitterFactor": 0.2
    }
  }'
```

### 4. View live execution dashboard

Open `http://localhost:8080/dashboard` in a web browser to watch the task stream, active node assignments, and timer accuracy in real time.

## Configuration options

Configure Chronos via `application.yml` or environment variables:

| Property | Default | Description |
|---|---|---|
| `chronos.cluster.node-id` | Auto-generated UUID | Unique identifier for the running instance |
| `chronos.cluster.heartbeat-interval-ms` | `3000` | Lease renewal frequency |
| `chronos.cluster.lease-duration-ms` | `10000` | Duration before an unattended task is treated as orphaned |
| `chronos.timer.tick-duration-ms` | `100` | Duration of each wheel tick in milliseconds |
| `chronos.timer.wheel-size` | `512` | Number of buckets in the circular wheel |
| `chronos.worker.batch-size` | `200` | Tasks fetched per database lease round |

## Testing and verification

Run unit and architectural rule tests:

```bash
mvn test
```

Run end-to-end integration tests using Testcontainers:

```bash
mvn verify -Dtest=*IntegrationTest
```

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
