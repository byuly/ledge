# ledge — Product Requirements Document

> **Status:** In Progress (v1 scope)
> **Version:** 1.3
> **Type:** Open-source self-hostable service + developer SDK

---

## Table of Contents

1. [Project Overview + Problem Statement](#1-project-overview--problem-statement)
2. [Goals + Non-Goals](#2-goals--non-goals)
3. [Target Users + Use Cases](#3-target-users--use-cases)
4. [System Architecture](#4-system-architecture)
5. [Data Models](#5-data-models)
6. [API Design](#6-api-design)
7. [Kafka Topology + Event Flows](#7-kafka-topology--event-flows)
8. [Storage Layer Design](#8-storage-layer-design)
9. [v1 Scope vs v2 Backlog](#9-v1-scope-vs-v2-backlog)
10. [Non-Functional Requirements](#10-non-functional-requirements)
11. [Open Questions + Decisions](#11-open-questions--decisions)
12. [Domain Architecture Decisions](#12-domain-architecture-decisions)
13. [Implementation Progress](#13-implementation-progress)

---

## 1. Project Overview + Problem Statement

### 1.1 Summary

**ledge** is an event-sourced memory infrastructure layer for AI agents. It captures every memory event — context loaded, inference made, memory read/written, tool called — as an immutable, versioned, timestamped record. It exposes point-in-time memory reconstruction, context diffing between sessions, and full audit trail queries over AI agent behavior.

It is open-source and self-hostable. The long-term commercial path is a managed cloud offering.

---

### 1.2 Tech Stack

| Layer | Technology | Rationale                                                                                                                                                                                                                                                                                           |
|---|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| JDK | Eclipse Temurin 21 (LTS) | Free, production-grade OpenJDK build by Eclipse Adoptium. JDK 21 brings Virtual Threads (Project Loom) — reactive throughput without fully reactive code everywhere. Spring Boot 3.2+ has first-class support. Use gradle wrapper. Use `jdk-alpine` in build stage, `jre-alpine` in runtime image.  |
| Runtime | Kotlin + Spring Boot 3.2+ + WebFlux | Reactive, non-blocking. Kotlin is concise and idiomatic for JVM backend. WebFlux handles high-throughput event streams without blocking threads. Virtual Threads complement WebFlux for mixed workloads.                                                                                            |
| Event Backbone | Apache Kafka | Immutable, append-only event log partitioned by `tenantId`. Kafka is the source of truth — not a transport layer. Events are never deleted.                                                                                                                                                         |
| Audit Storage | ClickHouse | Columnar, append-only. Optimized for time-range queries over billions of events. Point-in-time memory reconstruction queries run fast here. Postgres degrades badly at this volume.                                                                                                                 |
| Operational DB | PostgreSQL | Handles all relational, mutable, ACID-critical state: tenants, agents, sessions, live memory entries.                                                                                                                                                                                               |
| Cache | Redis | Hot path for active session context and frequently accessed memory entries. Target: sub-10ms retrieval for active sessions.                                                                                                                                                                         |
| Search | OpenSearch | Full-text search across memory content. v2 feature.                                                                                                                                                                                                                                                 |
| Metrics | Micrometer + Prometheus + Grafana | Micrometer is built into Spring Boot Actuator. Prometheus scrapes `/actuator/prometheus`. Grafana dashboards for all key operational and domain metrics. All included in `docker-compose.yml`.                                                                                                      |
| Deployment | Docker + Docker Compose (v1), Kubernetes Helm chart (v2) | Self-hostable by default.                                                                                                                                                                                                                                                                           |
| SDK Target | JVM-first (Kotlin/Java). REST clients for other languages. |                                                                                                                                                                                                                                                                                                     |

---

### 1.3 Problem

Enterprise AI deployments are unauditable by design. When an AI agent makes a decision — drafting a contract clause, triaging a support ticket, flagging a financial transaction — there is no reliable record of:

- What context the model had access to at inference time
- What it retrieved from memory across prior sessions
- How its knowledge state evolved between session A and session B
- Why it responded differently to the same prompt on two different days

Raw input/output logging exists but solves the wrong problem. Logs capture what was said. They do not capture what was **known**. There is no existing primitive for point-in-time memory reconstruction or knowledge-state diffing between sessions.

This is a hard blocker for regulated industries — finance, legal, healthcare, insurance — where AI-assisted decisions require explainability and auditability by law (GDPR, SOC 2, EU AI Act).

---

### 1.4 Solution

ledge sits alongside any AI agent deployment as an infrastructure layer. It captures every memory event as an immutable, versioned record and exposes:

- **Point-in-time reconstruction:** "What did agent X know at 14:47 on Tuesday?"
- **Context diffing:** "What changed in agent X's knowledge between session A and session B?"
- **Audit trail queries:** "Show all memory reads that preceded this inference"
- **Session replay:** Full reconstruction of the reasoning context for any past session
- **DOMAIN DRIVEN DESIGN:** Ubiquitous Language seems like an obvious thing, however, it is critical that we use the business language in code consciously and as a disciplined rule.

The system is event-sourced at its core — Kafka as the immutable spine, ClickHouse as the audit store, PostgreSQL for operational state, Redis for low-latency active session access.

---

### 1.5 What ledge Is Not

- Not an LLM provider or model wrapper
- Not a prompt management tool
- Not a vector database or RAG system
- Not an agent orchestration framework

It is pure infrastructure. It is indifferent to which model, agent framework, or AI provider the developer uses.

---

## 2. Goals + Non-Goals

### 2.1 Goals — v1

1. Capture every AI memory event — context loaded, inference requested/completed, memory read/written, tool called — as an immutable, append-only record
2. Expose a point-in-time query API: reconstruct full memory state for any agent at any timestamp
3. Expose a context diff API: compute what changed in an agent's knowledge between two points in time
4. Multi-tenant from day one: all data scoped to `tenantId`, hard isolation between tenants
5. Self-hostable with a single `docker compose up`: no external dependencies beyond the compose file
6. Developer SDK (Kotlin/Java) that integrates in under 30 minutes with any existing AI app
7. Kafka as the source of truth: all events flow through Kafka before touching any storage layer

---

### 2.2 Goals — v2 (explicitly not v1)

- Managed cloud offering
- OpenSearch full-text search across memory content
- Kubernetes Helm chart
- SDKs for Python, TypeScript, Go
- Memory decay / TTL-based eviction logic
- Multi-agent collaboration support
- Dashboard / UI for cognitive traces

---

### 2.3 Non-Goals (never, or out of scope by design)

- ledge does not execute AI inferences — it observes and records them
- ledge does not store raw model weights or embeddings
- ledge does not decide what an agent should remember — that is the agent's responsibility
- ledge does not provide a vector search layer — use a dedicated vector DB alongside it
- ledge is not a general-purpose logging tool — it is purpose-built for AI memory state

---

### 2.4 Success Criteria — v1

| Criteria | Target |
|---|---|
| Developer onboarding | First memory event recorded in under 30 minutes from SDK install |
| Point-in-time query correctness | Returns exact memory state for any past session |
| Context diff correctness | Correctly identifies added, removed, and modified memory entries between two snapshots |
| Throughput | 10,000 events/second without degradation on modest hardware (4 cores, 16GB RAM) |
| Tenant isolation | All data for a tenant is fully isolated and deletable (GDPR baseline) |

---

## 3. Target Users + Use Cases

### 3.1 Primary User — Backend/Platform Developer at an Enterprise

A developer responsible for integrating or maintaining an AI agent inside a regulated business. They do not need a UI. They need an SDK, a clean API, and confidence that the system will not lose data or leak across tenants.

**Their core jobs to be done:**
- Instrument an existing AI agent with memory event capture in minimal code
- Query memory state for a specific session when an incident or compliance request occurs
- Diff two sessions to explain why the agent behaved differently
- Delete all data for a specific user on GDPR request

---

### 3.2 Use Cases

**Use Case 1 — Compliance Audit**

A financial services company uses an AI agent to assist relationship managers with client recommendations. A regulator asks: "What information did your AI have access to when it suggested this product to this client on March 3rd?" The developer queries ledge for the memory snapshot at that timestamp and produces a full audit report.

**Use Case 2 — Incident Investigation**

An AI support agent gives a customer incorrect refund eligibility information. The support engineering team queries ledge: "What did the agent know during that session? Did it retrieve the correct policy document?" The context diff between the broken session and a working session reveals that a policy document had been evicted from memory before the session started.

**Use Case 3 — Regression Detection**

A developer deploys an updated AI agent. They diff memory snapshots before and after deployment across 100 sessions to verify that the agent's knowledge state is evolving as expected and no regressions have been introduced.

**Use Case 4 — GDPR Right to Erasure**

A user submits a data deletion request. The developer calls the tenant-scoped delete API. All memory events, snapshots, and entries associated with that user's sessions are purged from ClickHouse, PostgreSQL, and Redis.

---

## 4. System Architecture

### 4.1 High-Level Overview

```
┌─────────────────────────────────────────────────┐
│                  AI Agent App                   │
│                                                 │
│   ┌─────────────────────────────────────────┐   │
│   │         ledge SDK                │   │
│   │  (instruments agent, emits events)      │   │
│   └──────────────────┬──────────────────────┘   │
└──────────────────────┼──────────────────────────┘
                       │ HTTP (REST)
                       ▼
┌─────────────────────────────────────────────────┐
│            ledge Service                 │
│                                                 │
│   ┌─────────────┐      ┌─────────────────────┐  │
│   │  Ingest API │      │    Query API         │  │
│   │  (WebFlux)  │      │    (WebFlux)         │  │
│   └──────┬──────┘      └──────────┬──────────┘  │
│          │                        │              │
│          ▼                        ▼              │
│   ┌─────────────┐      ┌─────────────────────┐  │
│   │    Kafka    │      │       Redis          │  │
│   │  (event     │      │  (hot session cache) │  │
│   │   spine)    │      └─────────────────────┘  │
│   └──────┬──────┘                               │
│          │                                       │
│    ┌─────┴──────┐                               │
│    │  Consumers │                               │
│    └─────┬──────┘                               │
│          │                                       │
│    ┌─────┴──────────────────┐                   │
│    │                        │                   │
│    ▼                        ▼                   │
│ ┌──────────┐         ┌────────────┐             │
│ │ClickHouse│         │ PostgreSQL │             │
│ │(audit log│         │(operational│             │
│ │ storage) │         │   state)   │             │
│ └──────────┘         └────────────┘             │
└─────────────────────────────────────────────────┘
```

---

### 4.2 Component Responsibilities

| Component | Responsibility |
|---|---|
| **SDK** | Wraps agent interactions. Emits `MemoryEvent` records to the Ingest API. Handles batching and retry. |
| **Ingest API** | Validates incoming events, assigns sequence numbers, publishes to Kafka. Reactive (WebFlux). Returns `202 Accepted` immediately. |
| **Query API** | Serves point-in-time reconstruction, context diffs, audit trail queries. Reads from Redis (hot) or ClickHouse (cold). |
| **Kafka** | Source of truth. All events are published here first. Never truncated. Partitioned by `tenantId` for isolation and parallelism. |
| **Kafka Consumers** | Two consumer groups: (1) ClickHouse writer — persists all events for audit storage. (2) PostgreSQL writer — maintains live session and memory entry state. Both update Redis for active sessions. |
| **ClickHouse** | Append-only audit store. All `MemoryEvent` records. All `MemorySnapshot` records. Handles time-range queries. |
| **PostgreSQL** | Tenants, agents, sessions, live `MemoryEntry` state. ACID-critical. Source of relational truth. |
| **Redis** | Hot cache for active sessions. Stores current context window and recent memory entries per session. TTL-evicted when session ends. |

---

### 4.3 Data Flow — Event Ingestion

```
SDK emits event
      │
      ▼
POST /api/v1/events (Ingest API)
      │
      ├── validate schema
      ├── assign sequenceNumber (per-session monotonic)
      ├── assign eventId (UUID)
      │
      ▼
Publish to Kafka topic: memory.events.raw
      │
      ├── Consumer Group A → write to ClickHouse (memory_events table)
      └── Consumer Group B → write to PostgreSQL + update Redis
```

---

### 4.4 Data Flow — Point-in-Time Query

```
GET /api/v1/agents/{agentId}/memory?at=2024-01-16T14:47:00Z
      │
      ├── check Redis → cache hit? return immediately
      │
      └── cache miss →
            query ClickHouse:
              SELECT * FROM memory_snapshots
              WHERE agent_id = :agentId
                AND created_at <= :timestamp
              ORDER BY created_at DESC
              LIMIT 1
            → hydrate MemoryEntries
            → write to Redis cache
            → return response
```

---

### 4.5 Dockerfile + Build Configuration

Multi-stage build. JDK in build stage, JRE-only in runtime image to minimize attack surface and image size.

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
---

### 4.6 Docker Compose — Full Stack

See [`docker-compose.yml`](./docker-compose.yml). Run with:

```
docker compose up
```

**Services:** `ledge` (app), `kafka` (KRaft mode), `postgres`, `redis`, `clickhouse`, `prometheus`, `grafana`

**Requirements:**
- Copy `.env.example` → `.env` and set `POSTGRES_PASSWORD` and `GRAFANA_PASSWORD` before starting
- All services have healthchecks; `ledge` depends on all four infrastructure services
- Prometheus scrape config: `observability/prometheus.yml`
- Grafana datasource (Prometheus) auto-provisioned via `observability/grafana/provisioning/datasources/`
- Grafana available at `localhost:3000`; Prometheus at `localhost:9090`
- Database init SQL (`infra/sql/init.sql`, `infra/clickhouse/init.sql`) will be mounted once schema is defined

---

## 5. Data Models

### 5.1 MemoryEvent

The fundamental unit. Every interaction the AI has with its memory is a `MemoryEvent`. Immutable once written. Uses typed IDs from the shared kernel — never raw UUIDs.

```kotlin
// io.ledge.ingestion.domain.MemoryEvent — immutable data class
data class MemoryEvent(
    val id: EventId,              // globally unique, immutable (typed)
    val sessionId: SessionId,     // groups events into a conversation
    val agentId: AgentId,         // which AI agent produced this
    val tenantId: TenantId,       // multi-tenancy scope
    val eventType: EventType,     // see enum below
    val sequenceNumber: Long,     // monotonic per-session ordering (assigned by Session aggregate)
    val occurredAt: Instant,      // wall clock time, UTC
    val payload: String,          // event-type-specific JSON data
    val contextHash: ContextHash?,// SHA-256 value object (validated on construction)
    val parentEventId: EventId?,  // causal link to the event that triggered this one
    val schemaVersion: SchemaVersion // value object wrapping Int (must be positive)
)

enum class EventType {
    USER_MESSAGE,             // message from the user
    ASSISTANT_MESSAGE,        // response from the AI
    TOOL_CALL,                // external tool invoked
    TOOL_RESULT,              // tool returned data
    ERROR,                    // error during processing
    CONTEXT_SWITCH,           // context window changed
    FEEDBACK,                 // user feedback on agent behavior
    CORRECTION,               // user corrected the agent
    PREFERENCE_EXPRESSED,     // user stated a preference
    FACT_STATED,              // user or agent stated a fact
    TASK_COMPLETED            // a task was finished
}
```

The `contextHash` is a `ContextHash` value object wrapping a SHA-256 hex string (validated on construction — rejects non-SHA-256 values). Two identical prompts with different `contextHash` values means different knowledge was present. This is what makes audit queries meaningful.

`SchemaVersion` is a value object wrapping a positive Int — `SchemaVersion(0)` throws on construction.

---

### 5.2 Session

Groups `MemoryEvent` records into a logical conversation or task execution. This is an **aggregate root** — it enforces invariants (session must be ACTIVE to ingest events, sequence numbers are monotonically increasing).

```kotlin
// io.ledge.ingestion.domain.Session — aggregate root (not a data class)
class Session(
    val id: SessionId,
    val agentId: AgentId,
    val tenantId: TenantId,
    val startedAt: Instant,
    var endedAt: Instant?,
    var status: SessionStatus,            // ACTIVE, COMPLETED, ABANDONED
    var nextSequenceNumber: Long          // monotonic counter for event ordering
) {
    // Key method — enforces invariant: session must be ACTIVE, assigns sequence number
    fun ingest(eventId, eventType, payload, ...): MemoryEvent

    // Lifecycle transitions — enforce valid state machine
    fun complete()   // ACTIVE → COMPLETED (sets endedAt)
    fun abandon()    // ACTIVE → ABANDONED (sets endedAt)
}

enum class SessionStatus {
    ACTIVE, COMPLETED, ABANDONED
}
```

---

### 5.3 MemorySnapshot

A point-in-time capture of everything the agent "knew." Analogous to a git commit — references its parent, contains full state, is immutable once created. This is an **aggregate root** in the `memory` bounded context.

```kotlin
// io.ledge.memory.domain.MemorySnapshot — aggregate root
class MemorySnapshot(
    val id: SnapshotId,
    val agentId: AgentId,
    val tenantId: TenantId,
    val createdAt: Instant,
    val parentSnapshotId: SnapshotId?,    // linked list of memory states over time
    val snapshotHash: ContentHash,        // SHA-256 value object (validated)
    val entries: List<MemoryEntry>,
    val triggerEventId: EventId           // which MemoryEvent caused this snapshot
)
```

---

### 5.4 MemoryEntry

A discrete unit of knowledge the agent holds. Lives inside a `MemorySnapshot`.

```kotlin
// io.ledge.memory.domain.MemoryEntry — entity
data class MemoryEntry(
    val id: EntryId,
    val content: String,              // the actual fact or knowledge
    val contentHash: ContentHash,     // SHA-256 value object (validated)
    val entryType: MemoryEntryType,
    val confidence: Confidence,       // value object, validated 0.0..1.0
    val sourceEventId: EventId,       // which MemoryEvent created this entry
    val createdAt: Instant,
    val expiresAt: Instant?,          // optional TTL (v2 decay feature)
    val accessCount: Long,            // how many times retrieved
    val lastAccessedAt: Instant?
)

enum class MemoryEntryType {
    FACT,           // discrete piece of knowledge
    PREFERENCE,     // learned user or tenant behavior pattern
    PROCEDURE,      // reusable workflow the agent has learned
    EPISODIC,       // something that happened in a past session
    SUMMARY         // compressed representation of older memory
}
```

**Value objects** — `Confidence(1.5f)` throws `IllegalArgumentException` on construction. `ContentHash("")` throws. These enforce domain rules at the type level.

---

### 5.5 ContextDiff

The result of diffing two `MemorySnapshot` records. Core feature for compliance and incident investigation. This is a **computed value object** — not stored, produced on demand.

```kotlin
// io.ledge.memory.domain.ContextDiff — value object (computed, not stored)
data class ContextDiff(
    val fromSnapshotId: SnapshotId,
    val toSnapshotId: SnapshotId,
    val addedEntries: List<MemoryEntry>,
    val removedEntries: List<MemoryEntry>,
    val modifiedEntries: List<MemoryEntryDelta>
)

// io.ledge.memory.domain.MemoryEntryDelta — value object
data class MemoryEntryDelta(
    val entryId: EntryId,
    val before: MemoryEntry,
    val after: MemoryEntry
)
```

---

### 5.6 Tenant + Agent

`Tenant` is an **aggregate root** in the `tenant` bounded context — it enforces lifecycle transitions (can't suspend a deleted tenant). `Agent` is an entity within the tenant context.

```kotlin
// io.ledge.tenant.domain.Tenant — aggregate root (not a data class)
class Tenant(
    val id: TenantId,
    val name: String,
    val apiKeyHash: String,           // bcrypt hash, used for SDK auth
    var status: TenantStatus,         // ACTIVE, SUSPENDED, DELETED
    val createdAt: Instant
) {
    fun suspend()   // ACTIVE → SUSPENDED (throws if not ACTIVE)
    fun delete()    // ACTIVE|SUSPENDED → DELETED (throws if already DELETED)
}

enum class TenantStatus { ACTIVE, SUSPENDED, DELETED }

// io.ledge.tenant.domain.Agent — entity
data class Agent(
    val id: AgentId,
    val tenantId: TenantId,
    val name: String,
    val description: String,
    val createdAt: Instant,
    val metadata: Map<String, String>
)
```

---

## 6. API Design

All endpoints are REST over HTTP. All responses are JSON. All requests require an `X-API-Key` header scoped to a tenant. Tenant is inferred from the API key — never passed explicitly in request body.

Base URL: `/api/v1`

---

### 6.1 Event Ingestion

**POST** `/events`

Ingest a single memory event. Returns immediately (`202 Accepted`). Actual persistence is async via Kafka consumers.

```json
// Request body
{
  "sessionId": "uuid",
  "agentId": "uuid",
  "eventType": "ASSISTANT_MESSAGE",
  "occurredAt": "2024-01-16T14:47:00Z",
  "payload": { "prompt": "...", "response": "...", "modelId": "gpt-4o" },
  "contextHash": "sha256...",
  "parentEventId": "uuid | null",
  "schemaVersion": 1
}

// Response: 202 Accepted
{
  "eventId": "uuid",
  "sequenceNumber": 42
}
```

**POST** `/events/batch`

Ingest up to 500 events in a single request. Same async semantics.

```json
// Request body
{ "events": [ /* array of event objects */ ] }

// Response: 202 Accepted
{ "accepted": 500, "eventIds": [ "uuid", "uuid", "..." ] }
```

---

### 6.2 Session Management

**POST** `/sessions`
Create a new session. Returns `sessionId` used for all subsequent event ingestion.

**GET** `/sessions/{sessionId}`
Retrieve session metadata and status.

**PATCH** `/sessions/{sessionId}`
Update session status (e.g. mark as `COMPLETED` or `ABANDONED`).

**GET** `/sessions/{sessionId}/events`
Retrieve all events for a session in sequence order. Supports pagination via `?limit=` and `?after=sequenceNumber`.

---

### 6.3 Memory Queries (Core Feature)

**GET** `/agents/{agentId}/memory`

Point-in-time memory reconstruction. Returns the full memory state the agent held at the given timestamp.

```
Query params:
  at=ISO8601 timestamp (required)

Response:
{
  "snapshotId": "uuid",
  "agentId": "uuid",
  "resolvedAt": "2024-01-16T14:47:00Z",
  "entryCount": 42,
  "entries": [ /* array of MemoryEntry */ ]
}
```

**GET** `/agents/{agentId}/memory/diff`

Context diff between two points in time.

```
Query params:
  from=ISO8601 timestamp (required)
  to=ISO8601 timestamp (required)

Response:
{
  "diffId": "uuid",
  "from": "2024-01-16T14:00:00Z",
  "to": "2024-01-16T15:00:00Z",
  "addedCount": 3,
  "removedCount": 1,
  "modifiedCount": 2,
  "addedEntries": [ /* MemoryEntry */ ],
  "removedEntries": [ /* MemoryEntry */ ],
  "modifiedEntries": [ /* MemoryEntryDelta */ ]
}
```

**GET** `/sessions/{sessionId}/audit`

Full audit trail for a session. Returns all events in sequence with memory state at each step.

---

### 6.4 Tenant + Agent Management

**POST** `/tenants` — Create tenant (internal/admin only, not exposed in SDK)
**POST** `/agents` — Register a new agent under the authenticated tenant
**GET** `/agents` — List all agents for the tenant
**DELETE** `/tenants/{tenantId}` — Purge all data for tenant (GDPR erasure)

---

## 7. Kafka Topology + Event Flows

### 7.1 Topics

| Topic | Partitioning | Retention | Purpose |
|---|---|---|---|
| `memory.events.raw` | By `tenantId` | Indefinite (never deleted) | All raw `MemoryEvent` records. Source of truth. |
| `memory.sessions` | By `tenantId` | Indefinite | Session lifecycle events (created, completed, abandoned) |
| `memory.snapshots` | By `tenantId` | Indefinite | Snapshot creation and update events |
| `memory.dlq` | By `tenantId` | 30 days | Dead-letter queue for failed consumer processing |

---

### 7.2 Consumer Groups

**Group A: `clickhouse-writer`**
- Subscribes to: `memory.events.raw`, `memory.snapshots`
- Responsibility: Append all events to ClickHouse `memory_events` and `memory_snapshots` tables
- Failure behavior: Retry 3x with exponential backoff, then publish to `memory.dlq`
- Parallelism: One consumer per Kafka partition

**Group B: `postgres-redis-writer`**
- Subscribes to: `memory.events.raw`, `memory.sessions`
- Responsibility: Maintain live session state in PostgreSQL, update Redis hot cache for active sessions
- Failure behavior: Retry 3x with exponential backoff, then publish to `memory.dlq`
- Note: Redis writes are best-effort — a cache miss falls back to ClickHouse, never fails the request

---

### 7.3 Event Flow — Full Path

```
1. SDK calls POST /api/v1/events
2. Ingest API validates, assigns eventId + sequenceNumber
3. Ingest API publishes to memory.events.raw (partitioned by tenantId)
4. Ingest API returns 202 Accepted to SDK

Async (parallel):
5a. clickhouse-writer consumes event → appends to ClickHouse memory_events
5b. postgres-redis-writer consumes event →
      - upserts session state in PostgreSQL
      - writes event to Redis active session context (TTL: 24h)

On session COMPLETED:
6. postgres-redis-writer creates MemorySnapshot in PostgreSQL
7. Publishes snapshot event to memory.snapshots
8. clickhouse-writer persists snapshot to ClickHouse
9. Redis TTL for session context begins countdown
```

---

## 8. Storage Layer Design

### 8.1 ClickHouse Schema

```sql
-- All memory events. Append-only. Never updated.
CREATE TABLE memory_events (
    event_id        UUID,
    session_id      UUID,
    agent_id        UUID,
    tenant_id       UUID,
    event_type      String,
    sequence_number Int64,
    occurred_at     DateTime64(3, 'UTC'),
    payload         String,       -- JSON
    context_hash    String,
    parent_event_id Nullable(UUID),
    schema_version  Int32
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (tenant_id, agent_id, occurred_at, sequence_number);

-- All memory snapshots. Append-only.
CREATE TABLE memory_snapshots (
    snapshot_id         UUID,
    agent_id            UUID,
    tenant_id           UUID,
    created_at          DateTime64(3, 'UTC'),
    parent_snapshot_id  Nullable(UUID),
    snapshot_hash       String,
    trigger_event_id    UUID,
    entry_count         Int32
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_at)
ORDER BY (tenant_id, agent_id, created_at);

-- All memory entries per snapshot.
CREATE TABLE memory_entries (
    entry_id          UUID,
    snapshot_id       UUID,
    agent_id          UUID,
    tenant_id         UUID,
    content           String,
    content_hash      String,
    entry_type        String,
    confidence        Float32,
    source_event_id   UUID,
    created_at        DateTime64(3, 'UTC'),
    expires_at        Nullable(DateTime64(3, 'UTC')),
    access_count      Int32,
    last_accessed_at  Nullable(DateTime64(3, 'UTC'))
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_at)
ORDER BY (tenant_id, agent_id, snapshot_id, entry_id);
```

---

### 8.2 PostgreSQL Schema

```sql
CREATE TABLE tenants (
    tenant_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    api_key     TEXT NOT NULL UNIQUE,   -- stored as bcrypt hash
    status      TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE agents (
    agent_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(tenant_id),
    name        TEXT NOT NULL,
    description TEXT,
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sessions (
    session_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id            UUID NOT NULL REFERENCES agents(agent_id),
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at            TIMESTAMPTZ,
    status              TEXT NOT NULL DEFAULT 'ACTIVE',
    memory_snapshot_id  UUID,
    metadata            JSONB DEFAULT '{}'
);

CREATE TABLE memory_entries_live (
    entry_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id          UUID NOT NULL REFERENCES agents(agent_id),
    tenant_id         UUID NOT NULL REFERENCES tenants(tenant_id),
    content           TEXT NOT NULL,
    content_hash      TEXT NOT NULL,
    entry_type        TEXT NOT NULL,
    confidence        FLOAT NOT NULL,
    source_event_id   UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ,
    access_count      INT NOT NULL DEFAULT 0,
    last_accessed_at  TIMESTAMPTZ
);

-- Indexes
CREATE INDEX idx_sessions_agent_tenant ON sessions(agent_id, tenant_id);
CREATE INDEX idx_memory_entries_agent ON memory_entries_live(agent_id, tenant_id);
```

---

### 8.3 Redis Key Design

```
session:{sessionId}:context          → JSON array of recent MemoryEvent records (TTL: 24h)
session:{sessionId}:status           → session status string (TTL: 24h)
agent:{agentId}:memory:hot           → JSON array of top 50 most-accessed MemoryEntry records
snapshot:{snapshotId}:hash           → snapshot hash string (TTL: 7 days)
tenant:{tenantId}:ratelimit          → rolling request count for rate limiting (TTL: 1 min)
```

All Redis values are JSON-serialized. TTLs are enforced strictly — no stale reads. On cache miss, always fall back to ClickHouse or PostgreSQL.

---

## 9. v1 Scope vs v2 Backlog

### 9.1 v1 — Must Have

- [ ] Kafka setup with `memory.events.raw`, `memory.sessions`, `memory.snapshots`, `memory.dlq` topics
- [ ] Ingest API: `POST /events`, `POST /events/batch`
- [ ] Session API: create, get, update, list events
- [ ] ClickHouse writer consumer (Group A)
- [ ] PostgreSQL + Redis writer consumer (Group B)
- [ ] Point-in-time memory query: `GET /agents/{agentId}/memory?at=`
- [ ] Context diff: `GET /agents/{agentId}/memory/diff?from=&to=`
- [ ] Session audit trail: `GET /sessions/{sessionId}/audit`
- [ ] Tenant + Agent management endpoints
- [ ] GDPR tenant purge: `DELETE /tenants/{tenantId}`
- [ ] Kotlin/Java SDK with fluent builder API
- [x] `docker-compose.yml` with all dependencies (Kafka, ClickHouse, PostgreSQL, Redis, Prometheus, Grafana)
- [ ] API key authentication (bcrypt hashed, Redis rate limiting)
- [ ] Basic README with integration guide (30-minute onboarding target)

### 9.3 Implementation Progress

**Done**
- [x] Shared kernel — typed IDs (`TenantId`, `AgentId`, `SessionId`, `EventId`, `SnapshotId`, `EntryId`), `DomainEvent` sealed interface
- [x] `ingestion` domain — `Session` aggregate, `MemoryEvent`, `EventType`, `ContextHash`, `SchemaVersion`, `SessionStatus`
- [x] `memory` domain — `MemorySnapshot` aggregate, `MemoryEntry`, `ContextDiff`, `MemoryEntryDelta`, `Confidence`, `ContentHash`, `MemoryEntryType`
- [x] `tenant` domain — `Tenant` aggregate, `Agent`, `TenantStatus`
- [x] Domain-level test coverage (all invariants and value object validations)
- [x] `memory` application layer — `MemoryService` + port interfaces (`MemorySnapshotRepository`, `MemoryEntryRepository`, `DomainEventPublisher`)
- [x] Infrastructure & test environment — docker-compose, observability config, `.env.example`, Testcontainers smoke test

**Next**
- [ ] `ingestion` + `tenant` application layers (service classes + port interfaces)
- [ ] Storage schema — `infra/sql/init.sql` (PostgreSQL), `infra/clickhouse/init.sql`
- [ ] Infrastructure adapters — R2DBC repos, ClickHouse writer, Redis cache
- [ ] Kafka wiring — producer (Ingest API), Consumer Group A + B
- [ ] HTTP API layer — WebFlux controllers for all §6 endpoints
- [ ] Auth middleware — `X-API-Key` resolution

### 9.2 v2 — Backlog

- [ ] OpenSearch integration for full-text memory content search
- [ ] Memory decay / TTL-based eviction logic
- [ ] Kubernetes Helm chart
- [ ] Python, TypeScript, Go SDKs
- [ ] Multi-agent shared memory spaces
- [ ] Web dashboard for cognitive trace visualization
- [ ] Managed cloud offering (auth, billing, provisioning)
- [ ] Webhook support — notify external systems on memory state changes
- [ ] Export API — dump all memory events as NDJSON for external analysis

---

## 10. Non-Functional Requirements

### 10.1 Performance

| Metric | Target |
|---|---|
| Event ingestion latency (p99) | < 50ms (time from SDK call to `202 Accepted`) |
| Point-in-time query latency — hot (Redis hit) | < 10ms |
| Point-in-time query latency — cold (ClickHouse) | < 500ms |
| Context diff computation | < 1s for snapshots up to 10,000 entries |
| Throughput | 10,000 events/second sustained on 4 cores / 16GB RAM |
| Kafka consumer lag | < 5 seconds under normal load |

---

### 10.2 Reliability

- Kafka is the source of truth. If ClickHouse or PostgreSQL writers fail, events are not lost — they remain in Kafka and can be replayed.
- Dead-letter queue (`memory.dlq`) captures all consumer failures for manual inspection and replay.
- Redis is a cache, never a source of truth. All Redis reads have PostgreSQL/ClickHouse fallbacks.
- No data is deleted unless explicitly requested via the tenant purge API.

---

### 10.3 Security

- All API requests authenticated via `X-API-Key` header (tenant-scoped)
- API keys stored as bcrypt hashes in PostgreSQL
- All Kafka topics partitioned by `tenantId` — no cross-tenant reads possible at the consumer level
- ClickHouse and PostgreSQL queries always include `tenant_id` in WHERE clause — enforced at the repository layer, not the API layer
- TLS required for all external communication in production
- No plaintext secrets in `docker-compose.yml` — all secrets via environment variables

---

### 10.4 Observability

#### Logging

Structured JSON logging via SLF4J + Logback. Every log line emits `tenantId`, `agentId`, `sessionId`, and `traceId` as structured fields for easy filtering.

#### Metrics — Spring Boot Actuator + Micrometer + Prometheus

Micrometer is built into Spring Boot Actuator. Add `micrometer-registry-prometheus` and a `/actuator/prometheus` scrape endpoint is auto-configured. No custom server needed.

`application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus, info
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ledge
```

#### Domain Metrics to Instrument

The following must be manually instrumented in service code using `MeterRegistry`. JVM, HTTP, and thread pool metrics are auto-instrumented by Actuator.

```kotlin
// Events ingested — counter per event type and tenant
Counter.builder("ledge.events.ingested")
    .tag("tenant_id", tenantId)
    .tag("event_type", eventType.name)
    .register(meterRegistry)
    .increment()

// Kafka consumer lag — auto-exposed via Spring Kafka metrics binding
// Available as: kafka.consumer.records-lag-max

// ClickHouse write latency
Timer.builder("ledge.clickhouse.write.duration")
    .tag("table", tableName)
    .register(meterRegistry)
    .record { writeToClickHouse(event) }

// Redis cache hit/miss
Counter.builder("ledge.redis.cache.requests")
    .tag("result", if (hit) "hit" else "miss")
    .tag("cache", cacheName)
    .register(meterRegistry)
    .increment()

// Point-in-time query latency
Timer.builder("ledge.query.pointintime.duration")
    .tag("source", if (fromCache) "redis" else "clickhouse")
    .register(meterRegistry)
    .record { executeQuery() }

// Context diff computation latency
Timer.builder("ledge.query.diff.duration")
    .register(meterRegistry)
    .record { computeDiff() }

// Active sessions gauge
Gauge.builder("ledge.sessions.active") { sessionService.countActive() }
    .register(meterRegistry)
```

#### Prometheus Scrape Config

`observability/prometheus.yml`:
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: ledge
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['ledge:8080']

  - job_name: kafka
    static_configs:
      - targets: ['kafka-exporter:9308']

  - job_name: postgres
    static_configs:
      - targets: ['postgres-exporter:9187']

  - job_name: redis
    static_configs:
      - targets: ['redis-exporter:9121']

  - job_name: clickhouse
    metrics_path: /metrics
    static_configs:
      - targets: ['clickhouse:9363']
```

#### Grafana Dashboards

Grafana runs at `localhost:3000` (default credentials via `GRAFANA_PASSWORD` env var). Provision two dashboards via `observability/grafana/provisioning/`:

**Dashboard 1 — ledge Operations**
- Events ingested per second (by event type)
- Kafka consumer lag (both consumer groups)
- ClickHouse write latency p50/p95/p99
- Redis hit/miss ratio
- Active sessions gauge
- HTTP request rate and latency by endpoint

**Dashboard 2 — Query Performance**
- Point-in-time query latency p50/p95/p99 (Redis vs ClickHouse source)
- Context diff computation latency
- Cache hit rate trend over time
- ClickHouse query scan volume

#### Health Check

`GET /actuator/health` — returns aggregate health of all dependencies (Kafka, PostgreSQL, Redis, ClickHouse). Used by Docker Compose `healthcheck` and future Kubernetes liveness/readiness probes.

---

### 10.5 Developer Experience

- Single `docker compose up` brings up the full stack (Kafka, ClickHouse, PostgreSQL, Redis, ledge service)
- SDK installable via Maven/Gradle in one dependency declaration
- SDK requires no configuration beyond API key and base URL
- First event recorded in under 30 minutes from scratch

---

## 11. Open Questions + Decisions

| # | Question | Status | Decision |
|---|---|---|---|
| 1 | Should snapshot creation be triggered automatically on session end, or manually by the SDK caller? | **Decided** | Automatic on `COMPLETED` session status. SDK callers mark session complete, snapshot is created by the service. |
| 2 | Should `contextHash` be computed by the SDK or the service? | **Decided** | Computed by the SDK before sending. The service validates the hash but does not recompute it — the agent knows its own context window. |
| 3 | What is the maximum payload size per event? | **Open** | Candidate: 1MB per event, 10MB per batch. Needs load testing to validate. |
| 4 | Should ClickHouse be the query layer for point-in-time queries, or should PostgreSQL own current state and ClickHouse be purely historical? | **Decided** | PostgreSQL owns live/current state. ClickHouse owns all historical queries. Redis caches active sessions. |
| 5 | Should the SDK be blocking or fully reactive (Kotlin coroutines / Flow)? | **Decided** | Provide both: a synchronous blocking client and a coroutine-based async client. |
| 6 | How many Kafka partitions per topic in the default docker-compose setup? | **Open** | Candidate: 3 partitions per topic for local dev. Configurable via environment variable for production. |
| 7 | Should API keys support scoping (read-only vs write)? | **Backlog (v2)** | v1 keys are full-access per tenant. Scoped keys in v2. |

---

*Document maintained alongside the codebase. Update version number on any structural change.*

---

## 12. Domain Architecture Decisions

> Appended as part of DDD domain architecture setup.

### 12.1 Bounded Contexts (v1)

Three bounded contexts govern all domain code:

| Context | Responsibility | Aggregate Root |
|---|---|---|
| `ingestion` | Write path — capturing MemoryEvents, session lifecycle, event sequencing | `Session` |
| `memory` | Read path — point-in-time reconstruction, context diffing | `MemorySnapshot` |
| `tenant` | Identity, access, admin lifecycle | `Tenant` |

### 12.1.1 Future: Session as its own bounded context

Session currently lives inside `ingestion`, but it has its own invariants (lifecycle, sequencing) and will likely need its own Kafka topic (`memory.sessions.lifecycle`). When session complexity grows, extract it into a standalone `session` context without breaking existing contracts.

### 12.2 Context Communication

Contexts communicate via domain events through Kafka, never direct calls.

- `ingestion` → `SessionCompleted` → `memory` (triggers snapshot creation)
- `tenant` → `TenantPurged` → `ingestion`, `memory` (GDPR cascade)

API key resolution (tenant → tenantId) happens at the infrastructure layer (middleware), not by coupling domain models.

### 12.3 Shared Kernel

Typed identifiers used across all contexts as Kotlin `@JvmInline value class` wrapping `UUID`:

`TenantId`, `AgentId`, `SessionId`, `EventId`, `SnapshotId`, `EntryId`

These provide compile-time type safety with zero runtime overhead.

### 12.4 Ubiquitous Language (enforced in code)

| Term | Meaning | Never use |
|---|---|---|
| MemoryEvent | Immutable record of agent activity | Event, LogEntry, Record |
| Session | Bounded conversation / task execution | Conversation, Thread |
| MemorySnapshot | Point-in-time agent knowledge capture | State, Checkpoint |
| MemoryEntry | Discrete unit of knowledge in a snapshot | Item, Fact, Data |
| ContextDiff | Difference between two MemorySnapshots | Delta, Change |
| Tenant | Isolated customer/organization | User, Org, Account |
| Agent | AI agent being observed | Bot, Model, Client |
| Ingest | Accept and record a MemoryEvent | Process, Handle |
| Reconstruct | Build memory state at a point in time | Query, Fetch, Load |

### 12.5 Package Structure

```
io.ledge/
├── LedgeApplication.kt
├── shared/                   ← typed identifiers (shared kernel) + domain events
│   ├── TenantId.kt
│   ├── AgentId.kt
│   ├── SessionId.kt
│   ├── EventId.kt
│   ├── SnapshotId.kt
│   ├── EntryId.kt
│   └── DomainEvent.kt       ← sealed interface for cross-context events
├── ingestion/                ← write path
│   ├── domain/
│   │   ├── Session.kt        ← aggregate root
│   │   ├── SessionStatus.kt
│   │   ├── MemoryEvent.kt    ← immutable entity
│   │   ├── EventType.kt
│   │   ├── ContextHash.kt    ← value object (SHA-256)
│   │   └── SchemaVersion.kt  ← value object (positive Int)
│   ├── application/
│   ├── infrastructure/
│   └── api/
├── memory/                   ← read path, reconstruction, diffing
│   ├── domain/
│   │   ├── MemorySnapshot.kt ← aggregate root
│   │   ├── MemoryEntry.kt    ← entity
│   │   ├── MemoryEntryType.kt
│   │   ├── Confidence.kt     ← value object (0.0..1.0)
│   │   ├── ContentHash.kt    ← value object (SHA-256)
│   │   ├── ContextDiff.kt    ← value object (computed)
│   │   └── MemoryEntryDelta.kt
│   ├── application/
│   ├── infrastructure/
│   └── api/
└── tenant/                   ← identity, access, admin
    ├── domain/
    │   ├── Tenant.kt          ← aggregate root
    │   ├── TenantStatus.kt
    │   └── Agent.kt           ← entity
    ├── application/
    ├── infrastructure/
    └── api/
```

### 12.6 Domain Events (Cross-Context Communication)

Domain events are defined as a sealed interface in the shared kernel (`io.ledge.shared.DomainEvent`). These are the contracts for cross-context communication via Kafka:

| Event | Producer | Consumer(s) | Purpose |
|---|---|---|---|
| `SessionCompleted` | `ingestion` | `memory` | Triggers snapshot creation when a session ends |
| `TenantPurged` | `tenant` | `ingestion`, `memory` | GDPR cascade — purge all data for a tenant |

Each event carries typed IDs and an `occurredAt` timestamp.

### 12.7 Value Objects

Domain value objects enforce business rules at construction time. They are implemented as `@JvmInline value class` for zero runtime overhead:

| Value Object | Context | Validation |
|---|---|---|
| `ContextHash` | `ingestion` | Must be valid SHA-256 hex string (64 lowercase hex chars) |
| `SchemaVersion` | `ingestion` | Must be positive integer |
| `Confidence` | `memory` | Must be in range 0.0..1.0 |
| `ContentHash` | `memory` | Must be valid SHA-256 hex string (64 lowercase hex chars) |

### 12.8 Design Rules for Domain Layer

1. **No Spring annotations** — domain layer is pure Kotlin. No `@Entity`, `@Component`, `@Repository`, etc.
2. **Typed IDs everywhere** — `TenantId` not `UUID`. Shared kernel types from `io.ledge.shared`.
3. **Aggregate roots enforce invariants** — `Session.ingest()` checks status. `Tenant.suspend()` checks valid transition. These are not anemic data classes.
4. **Value objects validate on construction** — `Confidence(1.5f)` throws, `ContextHash("")` throws.
5. **Immutable where the domain says immutable** — `MemoryEvent` is a data class with val-only properties. Aggregate roots use var for mutable state they manage (status, sequence counters).
