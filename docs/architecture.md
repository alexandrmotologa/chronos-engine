# Chronos Engine Architecture

Chronos Engine separates concerns using Hexagonal Architecture (Ports and Adapters) and Domain-Driven Design (DDD). The architecture guarantees that core business logic remains independent of persistence technologies, web frameworks, and messaging drivers.

## Package organization

```
com.engine.chronos/
├── domain/                          # Pure Java 21: zero framework dependencies
│   ├── model/                       # Task aggregate, TaskId, ScheduleRule, RetryPolicy
│   ├── event/                       # Sealed domain events
│   ├── exception/                   # Domain rule violations and invariants
│   ├── port/
│   │   ├── in/                      # ScheduleTaskUseCase, CancelTaskUseCase, GetTaskQuery
│   │   └── out/                     # TaskRepositoryPort, DistributedLeasePort, TaskDispatcherPort
│   └── timer/                       # In-memory HashedWheelTimer
├── application/                     # Orchestration services and DTO mappers
│   ├── service/                     # TaskCommandService, TaskQueryService
│   └── dto/                         # Request, response, and policy configuration records
└── infrastructure/                  # Spring Boot, JPA, Webhook, and Kafka adapters
    ├── adapter/
    │   ├── in/rest/                 # REST controllers and RFC 7807 error handlers
    │   ├── in/scheduler/            # PartitionWorker and OrphanHarvester
    │   ├── out/persistence/         # JPA entities, Spring Data repositories, TaskRepositoryAdapter
    │   ├── out/lease/               # PostgreSQL SKIP LOCKED lease adapter
    │   └── out/dispatcher/          # Virtual-thread HTTP client and Kafka dispatchers
    ├── config/                      # Virtual thread executor and OpenAPI configuration
    └── dashboard/                   # Embedded HTML5/SSE live monitoring dashboard
```

## Architectural layers

### 1. Domain layer

The domain layer contains only standard Java standard library types. It contains:
- No annotations from Spring, Jakarta, or Hibernate.
- Sealed domain events describing state transitions.
- Value objects enforcing self-validation: `TaskId`, `ScheduleRule`, `RetryPolicy`, and `ExecutionLease`.
- In-memory `HashedWheelTimer` providing O(1) scheduling operations.

ArchUnit rules test this layer during continuous integration to prevent framework leakage.

### 2. Application layer

The application layer coordinates actions between inbound ports and outbound infrastructure:
- Validates idempotency keys prior to task insertion.
- Calculates exponential backoff with randomized jitter on task retries.
- Dispatches domain events to subscribers.

### 3. Infrastructure layer

The infrastructure layer implements interfaces defined in `domain.port.out` and exposes driving endpoints in `infrastructure.adapter.in`:
- **Persistence**: Maps domain aggregates to `TaskEntity` with optimistic locking (`@Version`).
- **Distributed Leasing**: Uses PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED` across 256 virtual buckets, preventing two nodes from executing the same task.
- **Virtual Thread Dispatchers**: Uses Java 21 `HttpClient` executing inside lightweight virtual threads. Each outgoing webhook runs in its own virtual thread, avoiding thread pool starvation even during downstream latency spikes.

## Distributed partition leasing model

The cluster divides the task space into 256 virtual partitions:

```
Partition Hash = abs(idempotencyKey.hashCode() % 256)
```

1. Each node registers a heartbeat in the database every three seconds.
2. Active nodes claim a subset of partitions using consistent hashing or cooperative locking.
3. Every second, the node queries `chronos_tasks` for tasks in its assigned partitions that are scheduled to run within the next buffer window (for example, thirty seconds).
4. Fetched tasks load into the local `HashedWheelTimer`.
5. When the timer ticks and fires, a virtual thread dispatches the payload to the target endpoint.
6. If a worker node crashes mid-execution, its lease expires. The `OrphanHarvester` process on surviving nodes detects expired leases and resets them to `SCHEDULED` for immediate pickup.
