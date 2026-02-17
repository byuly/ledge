# ledge — Product Requirements Document

> **Status:** In Progress (v1 scope)
> **Version:** 2.0
> **Type:** Open-source self-hostable service + developer SDK
> **Architecture:** Two-tier (v1: Observation Layer, v2: Knowledge Layer)

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

**ledge** is an event-sourced observability and knowledge infrastructure layer for AI agents, built on a two-tier architecture:

- **Observation Layer (v1):** Captures the full cognitive lifecycle — context windows, inference requests, reasoning traces, model responses, tool use, errors — as immutable, versioned, timestamped records. Every model call has a corresponding `CONTEXT_ASSEMBLED` event containing the exact content sent to the model. This enables point-in-time context reconstruction, context window diffing between inferences, and full audit trail queries over AI agent behavior.

- **Knowledge Layer (v2):** Extracts structured memory state (facts, preferences, procedures) from the observation event stream. Enables memory snapshots, snapshot chains, and semantic knowledge-state diffing.

It is open-source and self-hostable. The long-term commercial path is a managed cloud offering.

---

### 1.2 Tech Stack

| Layer | Technology | Rationale                                                                                                                                                                                                                                                                                           |
|---|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| JDK | Eclipse Temurin 21 (LTS) | Free, production-grade OpenJDK build by Eclipse Adoptium. JDK 21 brings Virtual Threads (Project Loom) — reactive throughput without fully reactive code everywhere. Spring Boot 3.2+ has first-class support. Use gradle wrapper. Use `jdk-alpine` in build stage, `jre-alpine` in runtime image.  |
| Runtime | Kotlin + Spring Boot 3.2+ + WebFlux | Reactive, non-blocking. Kotlin is concise and idiomatic for JVM backend. WebFlux handles high-throughput event streams without blocking threads. Virtual Threads complement WebFlux for mixed workloads.                                                                                            |
| Event Backbone | Apache Kafka | Immutable, append-only event log partitioned by `tenantId`. Kafka is the source of truth — not a transport layer. Events are never deleted.                                                                                                                                                         |
| Audit Storage | ClickHouse | Columnar, append-only. Optimized for time-range queries over billions of events. Point-in-time context reconstruction queries run fast here. Postgres degrades badly at this volume.                                                                                                                 |
| Operational DB | PostgreSQL | Handles all relational, mutable, ACID-critical state: tenants, agents, sessions.                                                                                                                                                                                               |
| Cache | Redis | Hot path for active session context and latest context windows. Target: sub-10ms retrieval for active sessions.                                                                                                                                                                         |
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

ledge sits alongside any AI agent deployment as an infrastructure layer. It captures every cognitive lifecycle event as an immutable, versioned record and exposes:

- **Point-in-time context reconstruction:** "What was literally in agent X's context window at 14:47 on Tuesday?"
- **Context window diffing:** "What changed in the context window between inference A and inference B?"
- **Full cognitive trace:** "Context assembly → model call → reasoning → response — show the complete chain for this inference"
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

1. Capture cognitive lifecycle events (`CONTEXT_ASSEMBLED`, `INFERENCE_REQUESTED`, `INFERENCE_COMPLETED`, `REASONING_TRACE`, `USER_INPUT`, `AGENT_OUTPUT`, `TOOL_INVOKED`, `TOOL_RESPONDED`, `ERROR`) as immutable, append-only records
2. Expose a point-in-time context query API: return the exact context window content for any agent at any past inference via `CONTEXT_ASSEMBLED` events
3. Expose a context diff API: compute what changed in an agent's context window between two points in time, diffing actual content (not just hash comparison)
4. Multi-tenant from day one: all data scoped to `tenantId`, hard isolation between tenants
5. Self-hostable with a single `docker compose up`: no external dependencies beyond the compose file
6. Developer SDK (Kotlin/Java) with cognitive lifecycle interceptors that integrate in under 30 minutes with any existing AI app
7. Kafka as the source of truth: all events flow through Kafka before touching any storage layer
8. Per-inference granularity: every model call produces a `CONTEXT_ASSEMBLED` event, enabling mid-session point-in-time queries without explicit snapshot management

---

### 2.2 Goals — v2 (explicitly not v1)

- Knowledge extraction pipeline (derive `MemoryEntry` records from observation events)
- Memory snapshot chains (linked `MemorySnapshot` records over time)
- Knowledge-state diffing (`MemoryEntry`-level semantic diffs)
- `ledge.knowledge` Kafka topic for knowledge extraction events
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
| Point-in-time query correctness | Returns exact context window content for any past inference |
| Context diff correctness | Correctly computes differences between two context windows |
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

A financial services company uses an AI agent to assist relationship managers with client recommendations. A regulator asks: "What information did your AI have access to when it suggested this product to this client on March 3rd?" The developer queries ledge for the `CONTEXT_ASSEMBLED` event at that timestamp and produces a full audit report showing the exact context window content.

**Use Case 2 — Incident Investigation**

An AI support agent gives a customer incorrect refund eligibility information. The support engineering team queries ledge: "What was in the agent's context window during that session? Did it include the correct policy document?" The context window diff between the broken inference and a working one reveals that a policy document was absent from the context window before the failing inference.

**Use Case 3 — Regression Detection**

A developer deploys an updated AI agent. They diff context windows before and after deployment across 100 sessions to verify that the agent's context assembly is functioning as expected and no regressions have been introduced.

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
| **SDK** | Instruments cognitive lifecycle (context assembly, inference, tool use). Emits observation events to the Ingest API. Handles batching and retry. |
| **Ingest API** | Validates incoming events, validates payload structure per EventType, assigns sequence numbers, publishes to Kafka. Reactive (WebFlux). Returns `202 Accepted` immediately. |
| **Query API** | Serves context reconstruction via `CONTEXT_ASSEMBLED` events, context diffs, audit trail queries. Reads from Redis (hot) or ClickHouse (cold). |
| **Kafka** | Source of truth. All events are published here first. Never truncated. Partitioned by `tenantId` for isolation and parallelism. |
| **Kafka Consumers** | Two consumer groups: (1) ClickHouse writer — persists all events for audit storage. (2) PostgreSQL + Redis writer — maintains live session state and context cache. |
| **ClickHouse** | Append-only audit store. All observation events. Handles time-range queries and point-in-time context reconstruction. |
| **PostgreSQL** | Tenants, agents, sessions. ACID-critical. Source of relational truth. |
| **Redis** | Hot cache for active sessions. Stores latest `CONTEXT_ASSEMBLED` payload per agent and active session context. TTL-evicted when session ends. |

---

### 4.3 Data Flow — Event Ingestion

```
SDK emits event
      │
      ▼
POST /api/v1/events (Ingest API)
      │
      ├── validate schema
      ├── validate payload structure per EventType
      ├── assign sequenceNumber (per-session monotonic)
      ├── assign eventId (UUID)
      │
      ▼
Publish to Kafka topic: ledge.events
      │
      ├── Consumer Group A → write to ClickHouse (memory_events table)
      └── Consumer Group B → write to PostgreSQL + update Redis
```

---

### 4.4 Data Flow — Point-in-Time Context Query

```
GET /api/v1/agents/{agentId}/context?at=2024-01-16T14:47:00Z
      │
      ├── check Redis → cache hit for latest CONTEXT_ASSEMBLED? return if timestamp matches
      │
      └── cache miss →
            query ClickHouse:
              SELECT * FROM memory_events
              WHERE agent_id = :agentId
                AND event_type = 'CONTEXT_ASSEMBLED'
                AND occurred_at <= :timestamp
              ORDER BY occurred_at DESC
              LIMIT 1
            → return full context window payload
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
- Database init SQL (`infra/sql/init.sql`, `infra/clickhouse/init.sql`) mounted via `docker-entrypoint-initdb.d` for auto-initialization on first startup

---

### 4.7 Two-Tier Architecture: Observation and Knowledge

#### The Distinction

ledge separates **observation** (raw truth) from **knowledge** (derived interpretation):

- **Observation events** record exactly what happened during the cognitive lifecycle — what was in the context window, what parameters were sent to the model, what the model returned, what tools were called. These are objective, immutable facts.
- **Knowledge events** interpret what was *learned* — extracting facts, preferences, and procedures from the observation stream. These are derived, potentially revisable interpretations.

#### Why Observation First

You cannot build trustworthy knowledge on incomplete observation. If the observation layer has gaps — missing context windows, unrecorded tool calls, absent reasoning traces — then any knowledge extracted from that stream is unverifiable. Observation is the audit foundation.

#### v1 Scope: Observation Layer

The keystone event is `CONTEXT_ASSEMBLED`: a full structured capture of everything sent to the model at inference time. Combined with `INFERENCE_REQUESTED`, `INFERENCE_COMPLETED`, and the other cognitive lifecycle events, v1 provides:

- Per-inference granularity — every model call has a `CONTEXT_ASSEMBLED` event, so mid-session point-in-time queries are naturally supported without explicit snapshot management
- Full cognitive trace — context assembly → request → reasoning → response → tool use, all causally linked via `parentEventId`
- Context window diffing on actual content, not just hash comparison

#### v2 Scope: Knowledge Layer

The knowledge layer will extract structured `MemoryEntry` records from the observation event stream, build `MemorySnapshot` chains over time, and enable semantic knowledge-state diffing. The `memory` bounded context's domain models (`MemorySnapshot`, `MemoryEntry`, `ContextDiff`) remain in the codebase but are not infrastructure-wired in v1.

#### Codebase Alignment

The `memory` bounded context's domain models stay in the codebase for v2 readiness. In v1, the `memory` context serves observation queries (read path for `CONTEXT_ASSEMBLED` lookups and context diffs). In v2, it takes on knowledge management (snapshots, entries, semantic diffs).

---

## 5. Data Models

### 5.1 MemoryEvent

The fundamental unit. Every step in the AI cognitive lifecycle is a `MemoryEvent`. Immutable once written. Uses typed IDs from the shared kernel — never raw UUIDs.

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
    val payload: String,          // structured JSON per EventType (see §5.7 Payload Schemas)
    val contextHash: ContextHash?,// SHA-256 for comparison/dedup; actual content in payload
    val parentEventId: EventId?,  // causal chaining within inference cycles
    val schemaVersion: SchemaVersion // value object wrapping Int (must be positive)
)
```

#### EventType — v1 Observation Types

```kotlin
enum class EventType {
    // --- v1: Observation Layer (cognitive lifecycle) ---
    CONTEXT_ASSEMBLED,    // full context window content before model call
    INFERENCE_REQUESTED,  // model call params (model ID, temperature, etc.)
    REASONING_TRACE,      // chain-of-thought / thinking tokens (optional)
    INFERENCE_COMPLETED,  // model response + usage stats + latency
    USER_INPUT,           // user message to agent
    AGENT_OUTPUT,         // agent response to user
    TOOL_INVOKED,         // tool call (name, params)
    TOOL_RESPONDED,       // tool result
    ERROR,                // error at any stage

    // --- v2: Knowledge Layer (deferred) ---
    // MEMORY_EXTRACTED,     // structured entry derived from events
    // MEMORY_UPDATED,       // existing entry modified
    // MEMORY_EXPIRED,       // entry TTL reached
    // FEEDBACK_RECEIVED,    // user feedback
    // CORRECTION_APPLIED,   // user correction
}
```

#### Migration from Previous EventType

| Old (current code) | New | Notes |
|---|---|---|
| `USER_MESSAGE` | `USER_INPUT` | Renamed |
| `ASSISTANT_MESSAGE` | `AGENT_OUTPUT` | Renamed — not all agents are "assistants" |
| `TOOL_CALL` | `TOOL_INVOKED` | Renamed |
| `TOOL_RESULT` | `TOOL_RESPONDED` | Renamed |
| `ERROR` | `ERROR` | Unchanged |
| `CONTEXT_SWITCH` | Removed | Replaced by `CONTEXT_ASSEMBLED` |
| `FEEDBACK` | `FEEDBACK_RECEIVED` (v2) | Deferred to Knowledge Layer |
| `CORRECTION` | `CORRECTION_APPLIED` (v2) | Deferred to Knowledge Layer |
| `PREFERENCE_EXPRESSED` | `MEMORY_EXTRACTED` (v2) | Subsumed by Knowledge Layer |
| `FACT_STATED` | `MEMORY_EXTRACTED` (v2) | Subsumed by Knowledge Layer |
| `TASK_COMPLETED` | Removed | Not a cognitive lifecycle event |

#### Field Notes

The `contextHash` is a `ContextHash` value object wrapping a SHA-256 hex string (validated on construction — rejects non-SHA-256 values). It enables fast comparison and deduplication — two events with the same `contextHash` had identical context. The actual context content is captured in the `CONTEXT_ASSEMBLED` event payload.

`parentEventId` enables causal chaining within inference cycles. For example, `INFERENCE_COMPLETED` links to its `INFERENCE_REQUESTED`, and `TOOL_RESPONDED` links to its `TOOL_INVOKED`.

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

> **Tier:** Knowledge Layer (v2). Domain model implemented in codebase but not infrastructure-wired in v1.

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

> **Tier:** Knowledge Layer (v2). Not created in v1. Domain model remains for v2.

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

### 5.5 ObservationDiff + ContextDiff

#### ObservationDiff (v1 — Observation Layer)

The result of diffing two `CONTEXT_ASSEMBLED` event payloads. Compares actual context window content at the `ContentBlock` level. This is a **computed value object** — not stored, produced on demand.

```kotlin
// io.ledge.memory.domain.ObservationDiff — value object (computed, not stored)
data class ObservationDiff(
    val fromEventId: EventId,         // CONTEXT_ASSEMBLED event A
    val toEventId: EventId,           // CONTEXT_ASSEMBLED event B
    val addedBlocks: List<ContentBlock>,
    val removedBlocks: List<ContentBlock>,
    val modifiedBlocks: List<ContentBlockDelta>
)

// io.ledge.memory.domain.ContentBlock — value object
data class ContentBlock(
    val blockType: String,            // e.g. "system", "user", "tool_result", "document"
    val content: String,
    val tokenCount: Int,
    val source: String?               // optional provenance
)

// io.ledge.memory.domain.ContentBlockDelta — value object
data class ContentBlockDelta(
    val blockType: String,
    val before: ContentBlock,
    val after: ContentBlock
)
```

#### ContextDiff (v2 — Knowledge Layer)

The result of diffing two `MemorySnapshot` records. Compares `MemoryEntry`-level knowledge state. Core feature for compliance and incident investigation at the knowledge layer.

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
    val apiKeyHash: String,           // SHA-256 hash, used for SDK auth
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

### 5.7 Payload Schemas (v1 EventTypes)

Each v1 `EventType` has a defined JSON payload structure. The `payload` field in `MemoryEvent` must conform to the schema for its `eventType`.

#### CONTEXT_ASSEMBLED

The keystone event. Captures the full content of the context window sent to the model.

```json
{
  "blocks": [
    {
      "blockType": "system",
      "content": "You are a helpful assistant...",
      "tokenCount": 42,
      "source": "system_prompt"
    },
    {
      "blockType": "user",
      "content": "What is the refund policy?",
      "tokenCount": 8,
      "source": null
    }
  ],
  "totalTokens": 50
}
```

#### INFERENCE_REQUESTED

Model call parameters.

```json
{
  "modelId": "gpt-4o",
  "provider": "openai",
  "temperature": 0.7,
  "maxTokens": 4096,
  "topP": 1.0,
  "toolChoice": "auto"
}
```

#### REASONING_TRACE

Chain-of-thought or thinking tokens (optional — not all models produce these).

```json
{
  "thinkingContent": "Let me analyze the user's question about refund policy...",
  "thinkingTokenCount": 156
}
```

#### INFERENCE_COMPLETED

Model response with usage statistics and latency.

```json
{
  "response": "Our refund policy allows returns within 30 days...",
  "finishReason": "stop",
  "usage": {
    "promptTokens": 50,
    "completionTokens": 87,
    "totalTokens": 137
  },
  "latencyMs": 1243,
  "modelId": "gpt-4o"
}
```

#### USER_INPUT

User message to the agent.

```json
{
  "content": "What is the refund policy for electronics?",
  "inputType": "text",
  "metadata": {}
}
```

#### AGENT_OUTPUT

Agent response to the user.

```json
{
  "content": "Our refund policy allows returns within 30 days...",
  "outputType": "text",
  "inferenceEventId": "uuid-of-inference-completed-event"
}
```

#### TOOL_INVOKED

Tool call with name and parameters.

```json
{
  "toolName": "search_knowledge_base",
  "toolId": "call_abc123",
  "parameters": {
    "query": "refund policy electronics",
    "limit": 5
  }
}
```

#### TOOL_RESPONDED

Tool result.

```json
{
  "toolName": "search_knowledge_base",
  "toolInvokedEventId": "uuid-of-tool-invoked-event",
  "result": "{ \"documents\": [...] }",
  "durationMs": 234,
  "success": true
}
```

#### ERROR

Error at any stage.

```json
{
  "errorType": "RATE_LIMIT_EXCEEDED",
  "message": "OpenAI rate limit exceeded. Retry after 30s.",
  "relatedEventId": "uuid-of-inference-requested-event",
  "recoverable": true
}
```

---

## 6. API Design

All endpoints are REST over HTTP. All responses are JSON. All requests require an `X-API-Key` header scoped to a tenant. Tenant is inferred from the API key — never passed explicitly in request body.

> **Auth middleware (`ApiKeyAuthFilter`):** External callers send `X-API-Key`. The filter resolves the key to a `Tenant` via SHA-256 hash lookup, validates tenant status, applies Redis rate limiting, then injects `X-Tenant-Id` as a synthetic request header. All controllers receive `X-Tenant-Id` from the middleware — they never see `X-API-Key` directly. Exempt paths (no auth required): `POST /api/v1/tenants`, `DELETE /api/v1/tenants/{tenantId}`, `/actuator/**`.

Base URL: `/api/v1`

---

### 6.1 Event Ingestion

**POST** `/events`

Ingest a single observation event. Returns immediately (`202 Accepted`). Actual persistence is async via Kafka consumers.

```json
// Request body
{
  "sessionId": "uuid",
  "agentId": "uuid",
  "eventType": "CONTEXT_ASSEMBLED",
  "occurredAt": "2024-01-16T14:47:00Z",
  "payload": {
    "blocks": [
      { "blockType": "system", "content": "You are a helpful assistant...", "tokenCount": 42, "source": "system_prompt" },
      { "blockType": "user", "content": "What is the refund policy?", "tokenCount": 8, "source": null }
    ],
    "totalTokens": 50
  },
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
{ "accepted": 500, "results": [ { "eventId": "uuid", "sequenceNumber": 1 }, ... ] }
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

### 6.3 Observation Queries (Core Feature)

**GET** `/agents/{agentId}/context`

Point-in-time context reconstruction. Returns the exact context window content the agent had at the given timestamp, via the nearest preceding `CONTEXT_ASSEMBLED` event.

```
Query params:
  at=ISO8601 timestamp (required)

Response:
{
  "eventId": "uuid",
  "agentId": "uuid",
  "occurredAt": "2024-01-16T14:47:00Z",
  "payload": {
    "blocks": [
      { "blockType": "system", "content": "...", "tokenCount": 42, "source": "system_prompt" },
      { "blockType": "user", "content": "...", "tokenCount": 8, "source": null }
    ],
    "totalTokens": 50
  },
  "contextHash": "sha256..."
}
```

**GET** `/agents/{agentId}/context/diff`

Context window diff between two points in time. Compares two `CONTEXT_ASSEMBLED` payloads at the `ContentBlock` level.

```
Query params:
  from=ISO8601 timestamp (required)
  to=ISO8601 timestamp (required)

Response:
{
  "fromEventId": "uuid",
  "toEventId": "uuid",
  "from": "2024-01-16T14:00:00Z",
  "to": "2024-01-16T15:00:00Z",
  "addedBlocks": [ /* ContentBlock */ ],
  "removedBlocks": [ /* ContentBlock */ ],
  "modifiedBlocks": [ /* ContentBlockDelta */ ]
}
```

**GET** `/sessions/{sessionId}/audit`

Full cognitive trace for a session. Returns all events in sequence with inference cycles grouped (CONTEXT_ASSEMBLED → INFERENCE_REQUESTED → REASONING_TRACE → INFERENCE_COMPLETED).

---

### 6.4 Tenant + Agent Management

**POST** `/tenants` — Create tenant (internal/admin only, not exposed in SDK)
**POST** `/agents` — Register a new agent under the authenticated tenant
**GET** `/agents` — List all agents for the tenant
**DELETE** `/tenants/{tenantId}` — Purge all data for tenant (GDPR erasure)

---

## 7. Kafka Topology + Event Flows

### 7.1 Topics

| Topic | Tier | Partitioning | Retention | Purpose |
|---|---|---|---|---|
| `ledge.events` | v1 | By `tenantId` | Indefinite (never deleted) | All observation events. Source of truth. |
| `ledge.dlq` | v1 | By `tenantId` | 30 days | Dead-letter queue for failed consumer processing. |
| `ledge.knowledge` | v2 | By `tenantId` | Indefinite | Knowledge extraction events. |

---

### 7.2 Consumer Groups

**Group A: `clickhouse-writer`**
- Subscribes to: `ledge.events`
- Responsibility: Append all events to ClickHouse `memory_events` table
- Failure behavior: Retry 3x with exponential backoff, then publish to `ledge.dlq`
- Parallelism: One consumer per Kafka partition

**Group B: `postgres-redis-writer`**
- Subscribes to: `ledge.events`
- Responsibility: Maintain live session state in PostgreSQL, update Redis context cache for active sessions
- Failure behavior: Retry 3x with exponential backoff, then publish to `ledge.dlq`
- Note: Redis writes are best-effort — a cache miss falls back to ClickHouse, never fails the request

---

### 7.3 Event Flow — Full Path

```
1. SDK calls POST /api/v1/events
2. Ingest API validates, assigns eventId + sequenceNumber
3. Ingest API publishes to ledge.events (partitioned by tenantId)
4. Ingest API returns 202 Accepted to SDK

Async (parallel):
5a. clickhouse-writer consumes event → appends to ClickHouse memory_events
5b. postgres-redis-writer consumes event →
      - upserts session state in PostgreSQL
      - if CONTEXT_ASSEMBLED: writes payload to Redis context cache
      - writes event to Redis active session context (TTL: 24h)

On session COMPLETED:
6. postgres-redis-writer updates session status in PostgreSQL
7. Redis TTL for session context begins countdown
```

---

## 8. Storage Layer Design

### 8.1 ClickHouse Schema

```sql
-- All observation events. Append-only. Never updated.
CREATE TABLE memory_events (
    event_id        UUID,
    session_id      UUID,
    agent_id        UUID,
    tenant_id       UUID,
    event_type      LowCardinality(String),  -- v1: CONTEXT_ASSEMBLED, INFERENCE_REQUESTED, etc.
    sequence_number Int64,
    occurred_at     DateTime64(3, 'UTC'),
    payload         String,       -- structured JSON per EventType (see §5.7)
    context_hash    String,
    parent_event_id Nullable(UUID),
    schema_version  Int32
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (tenant_id, agent_id, occurred_at, sequence_number);

-- Materialized view for fast CONTEXT_ASSEMBLED point-in-time queries
CREATE MATERIALIZED VIEW context_assembled_mv
ENGINE = MergeTree()
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (tenant_id, agent_id, occurred_at)
AS SELECT
    event_id,
    session_id,
    agent_id,
    tenant_id,
    occurred_at,
    payload,
    context_hash
FROM memory_events
WHERE event_type = 'CONTEXT_ASSEMBLED';

-- v2: Knowledge Layer tables (not infrastructure-wired in v1)
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

-- v2: All memory entries per snapshot.
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
    api_key_hash TEXT NOT NULL UNIQUE,   -- SHA-256 hash
    status      TEXT NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED | DELETED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE agents (
    agent_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(tenant_id),
    name        TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sessions (
    session_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id            UUID NOT NULL REFERENCES agents(agent_id),
    tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at            TIMESTAMPTZ,
    status              TEXT NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | COMPLETED | ABANDONED
    next_sequence_number BIGINT NOT NULL DEFAULT 1,
    metadata            JSONB DEFAULT '{}'
);

-- v2: Knowledge Layer — live memory entries managed by knowledge extraction pipeline
-- CREATE TABLE memory_entries_live ( ... );

-- Indexes
CREATE INDEX idx_sessions_agent_tenant ON sessions(agent_id, tenant_id);
-- v2: CREATE INDEX idx_memory_entries_agent ON memory_entries_live(agent_id, tenant_id);
```

---

### 8.3 Redis Key Design

```
session:{sessionId}:context          → latest CONTEXT_ASSEMBLED payload for the session (TTL: 24h)
session:{sessionId}:status           → session status string (TTL: 24h)
agent:{agentId}:context:latest       → latest CONTEXT_ASSEMBLED payload for the agent (TTL: 24h)
tenant:{tenantId}:ratelimit          → rolling request count for rate limiting (TTL: 1 min)
```

All Redis values are JSON-serialized. TTLs are enforced strictly — no stale reads. On cache miss, always fall back to ClickHouse or PostgreSQL.

---

## 9. v1 Scope vs v2 Backlog

### 9.1 v1 — Must Have

- [ ] Kafka setup with `ledge.events`, `ledge.dlq` topics
- [ ] Ingest API: `POST /events`, `POST /events/batch`
- [ ] Payload validation per EventType (structured JSON schemas — see §5.7)
- [ ] Session API: create, get, update, list events
- [ ] ClickHouse writer consumer (Group A)
- [ ] PostgreSQL + Redis writer consumer (Group B)
- [ ] Context query: `GET /agents/{agentId}/context?at=`
- [ ] Context diff: `GET /agents/{agentId}/context/diff?from=&to=`
- [ ] Session audit trail: `GET /sessions/{sessionId}/audit`
- [ ] Tenant + Agent management endpoints
- [ ] GDPR tenant purge: `DELETE /tenants/{tenantId}`
- [ ] Kotlin/Java SDK with cognitive lifecycle interceptors
- [x] `docker-compose.yml` with all dependencies (Kafka, ClickHouse, PostgreSQL, Redis, Prometheus, Grafana)
- [x] API key authentication (SHA-256 hashed, Redis rate limiting)
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
- [x] `ingestion` application layer — `IngestionService` + port interfaces (`SessionRepository`, `MemoryEventPublisher`, `DomainEventPublisher`, `MemoryEventQuery`) + command/result DTOs
- [x] `tenant` application layer — `TenantService` + port interfaces (`TenantRepository`, `AgentRepository`, `DomainEventPublisher`) + command DTOs (`CreateTenantCommand`, `RegisterAgentCommand`)

**Next — 8 Phases**
1. [x] Domain model update — EventType enum (observation taxonomy), payload schemas, `ObservationDiff` + `ContentBlock` value objects
2. [x] Storage schema — `infra/sql/init.sql` (PostgreSQL), `infra/clickhouse/init.sql` + `context_assembled_mv` materialized view
3. [x] Infrastructure adapters — R2DBC repos, ClickHouse writer, Redis context cache
4. [x] Kafka pipeline — `ledge.events` + `ledge.dlq` topics, producer, consumer groups A + B
5. [x] HTTP API — write path: event ingestion, session management, tenant/agent endpoints
6. [x] HTTP API — read path: context query, context diff, audit trail endpoints
7. [x] Auth middleware — `X-API-Key` resolution, rate limiting
8. [ ] SDK — Kotlin/Java cognitive lifecycle interceptors (auto-capture + explicit instrumentation)

### 9.2 v2 — Backlog

- [ ] Knowledge extraction pipeline (derive `MemoryEntry` from observation events)
- [ ] Memory snapshot wiring (automatic snapshot creation on session completion)
- [ ] Knowledge-state diffing (`MemoryEntry`-level semantic diffs)
- [ ] `ledge.knowledge` Kafka topic for knowledge extraction events
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
| Context diff computation | < 1s for context windows up to 200 content blocks |
| Throughput | 10,000 events/second sustained on 4 cores / 16GB RAM |
| Kafka consumer lag | < 5 seconds under normal load |

---

### 10.2 Reliability

- Kafka is the source of truth. If ClickHouse or PostgreSQL writers fail, events are not lost — they remain in Kafka and can be replayed.
- Dead-letter queue (`ledge.dlq`) captures all consumer failures for manual inspection and replay.
- Redis is a cache, never a source of truth. All Redis reads have PostgreSQL/ClickHouse fallbacks.
- No data is deleted unless explicitly requested via the tenant purge API.

---

### 10.3 Security

- All API requests authenticated via `X-API-Key` header (tenant-scoped)
- API keys stored as SHA-256 hashes in PostgreSQL
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
| 1 | Should snapshot creation be triggered automatically on session end, or manually by the SDK caller? | **Superseded by v2** | In v1 (Observation Layer), there are no snapshots — every model call has a `CONTEXT_ASSEMBLED` event that captures the full context window. Snapshot creation is a v2 Knowledge Layer concern. |
| 2 | Should `contextHash` be computed by the SDK or the service? | **Decided** | Computed by the SDK before sending. The service validates the hash but does not recompute it — the agent knows its own context window. |
| 3 | What is the maximum payload size per event? | **Open** | Candidate: 1MB per event, 10MB per batch. Needs load testing to validate. `CONTEXT_ASSEMBLED` payloads may be large. |
| 4 | Should ClickHouse be the query layer for point-in-time queries, or should PostgreSQL own current state and ClickHouse be purely historical? | **Decided** | ClickHouse owns point-in-time context queries via the `context_assembled_mv` materialized view. PostgreSQL owns session state. Redis caches active context windows. |
| 5 | Should the SDK be blocking or fully reactive (Kotlin coroutines / Flow)? | **Decided** | Provide both: a synchronous blocking client and a coroutine-based async client. |
| 6 | How many Kafka partitions per topic in the default docker-compose setup? | **Open** | Candidate: 3 partitions per topic for local dev. Configurable via environment variable for production. |
| 7 | Should API keys support scoping (read-only vs write)? | **Backlog (v2)** | v1 keys are full-access per tenant. Scoped keys in v2. |
| 8 | Should `CONTEXT_ASSEMBLED` payloads be compressed in ClickHouse? | **Open** | Context windows can be large (100K+ tokens). ClickHouse supports `CODEC(ZSTD)` on String columns. Needs benchmarking for query-time decompression cost vs storage savings. |
| 9 | SDK auto-capture vs explicit instrumentation? | **Decided** | Both. The SDK provides automatic interceptors for common frameworks (e.g. Spring AI, LangChain4j) and explicit API for manual instrumentation. Auto-capture for convenience, explicit for precision. |
| 10 | Should `REASONING_TRACE` be captured as streaming chunks or buffered? | **Open** | Streaming captures partial thoughts if inference fails mid-stream, but adds complexity. Buffered is simpler but loses data on failure. Candidate: buffered for v1, streaming option in v2. |
| 11 | Redis cache optimization for context queries | **Backlog** | PRD §4.4 describes Redis cache check before ClickHouse for context queries. Currently the read path goes directly to ClickHouse via `ObservationEventQuery`. Redis is already populated by the Kafka consumer (Phase 4). Optimizing the query path to check Redis first is a performance enhancement, not a correctness requirement. Follow-up performance task. |
| 12 | API key hashing algorithm: bcrypt vs SHA-256 | **Decided** | PRD §10.3 originally specified bcrypt. Changed to SHA-256 (Rule 4c — obvious, low-risk). `TenantRepository.findByApiKeyHash` uses a direct equality lookup (`WHERE api_key_hash = :hash`), which requires deterministic hashing. BCrypt is non-deterministic (salted) and cannot support O(1) index lookup. API keys are 256-bit random strings, not low-entropy passwords — bcrypt's brute-force resistance is irrelevant. SHA-256 is the industry standard for API keys (GitHub, Stripe, Heroku) and supports index lookup. No code changes to `TenantRepository`, `R2dbcTenantRepository`, or `Tenant` domain. |

---

*Document maintained alongside the codebase. Update version number on any structural change.*

---

## 12. Domain Architecture Decisions

> Appended as part of DDD domain architecture setup.

### 12.1 Bounded Contexts

Three bounded contexts govern all domain code:

| Context | v1 Role | v2 Role | Aggregate Root |
|---|---|---|---|
| `ingestion` | Write path — capturing observation events, session lifecycle, event sequencing | Same | `Session` |
| `memory` | Read path — observation queries (`CONTEXT_ASSEMBLED` lookups, context window diffs) | Knowledge management (snapshots, entries, semantic diffs) | `MemorySnapshot` |
| `tenant` | Identity, access, admin lifecycle | Same | `Tenant` |

### 12.1.1 Future: Session as its own bounded context

Session currently lives inside `ingestion`, but it has its own invariants (lifecycle, sequencing) and will likely need its own Kafka topic (`memory.sessions.lifecycle`). When session complexity grows, extract it into a standalone `session` context without breaking existing contracts.

### 12.2 Context Communication

Contexts communicate via domain events through Kafka, never direct calls.

- `ingestion` → `SessionCompleted` → published but no consumer in v1 (future v2 use: triggers snapshot creation in `memory`)
- `tenant` → `TenantPurged` → `ingestion` (GDPR cascade delete)

API key resolution (tenant → tenantId) happens at the infrastructure layer (middleware), not by coupling domain models.

### 12.3 Shared Kernel

Typed identifiers used across all contexts as Kotlin `@JvmInline value class` wrapping `UUID`:

`TenantId`, `AgentId`, `SessionId`, `EventId`, `SnapshotId`, `EntryId`

These provide compile-time type safety with zero runtime overhead.

### 12.4 Ubiquitous Language (enforced in code)

| Term | Meaning | Never use |
|---|---|---|
| MemoryEvent | Immutable record of a cognitive lifecycle step (ObservationEvent) | Event, LogEntry, Record |
| Session | Bounded conversation / task execution | Conversation, Thread |
| MemorySnapshot | Point-in-time agent knowledge capture (v2 Knowledge Layer) | State, Checkpoint |
| MemoryEntry | Discrete unit of knowledge in a snapshot (v2 Knowledge Layer) | Item, Fact, Data |
| ContextDiff | Difference between two MemorySnapshots (v2 Knowledge Layer) | Delta, Change |
| ObservationDiff | Difference between two context windows (v1 Observation Layer) | — |
| CognitiveTrace | Complete event sequence for one inference cycle | — |
| ContextWindow | Full assembled content sent to model at inference time | Prompt, Input |
| InferenceCycle | Context assembly → request → reasoning → response | — |
| ObservationEvent | Immutable record of a cognitive lifecycle step | — |
| ContentBlock | A discrete unit within a context window (system prompt, user message, tool result, etc.) | — |
| Tenant | Isolated customer/organization | User, Org, Account |
| Agent | AI agent being observed | Bot, Model, Client |
| Ingest | Accept and record a MemoryEvent | Process, Handle |
| Reconstruct | Build context state at a point in time | Query, Fetch, Load |

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
│   │   ├── IngestionService.kt       ← application service
│   │   ├── CreateSessionCommand.kt   ← command DTO
│   │   ├── IngestEventCommand.kt     ← command DTO
│   │   ├── IngestEventResult.kt      ← result DTO
│   │   └── port/
│   │       ├── SessionRepository.kt      ← driven port
│   │       ├── MemoryEventPublisher.kt   ← driven port (Kafka write)
│   │       ├── DomainEventPublisher.kt   ← driven port (domain events)
│   │       └── MemoryEventQuery.kt       ← driven port (CQRS read)
│   ├── infrastructure/
│   └── api/
├── memory/                   ← read path (v1: observation queries, v2: knowledge management)
│   ├── domain/
│   │   ├── MemorySnapshot.kt ← aggregate root (v2)
│   │   ├── MemoryEntry.kt    ← entity (v2)
│   │   ├── MemoryEntryType.kt
│   │   ├── Confidence.kt     ← value object (0.0..1.0)
│   │   ├── ContentHash.kt    ← value object (SHA-256)
│   │   ├── ObservationDiff.kt   ← value object (v1, computed)
│   │   ├── ContentBlock.kt      ← value object (v1)
│   │   ├── ContentBlockDelta.kt ← value object (v1)
│   │   ├── ContextDiff.kt    ← value object (v2, computed)
│   │   └── MemoryEntryDelta.kt
│   ├── application/
│   │   ├── MemoryService.kt          ← application service
│   │   └── port/
│   │       ├── MemorySnapshotRepository.kt  ← driven port
│   │       ├── MemoryEntryRepository.kt     ← driven port
│   │       └── DomainEventPublisher.kt      ← driven port
│   ├── infrastructure/
│   └── api/
└── tenant/                   ← identity, access, admin
    ├── domain/
    │   ├── Tenant.kt          ← aggregate root
    │   ├── TenantStatus.kt
    │   └── Agent.kt           ← entity
    ├── application/
    │   ├── TenantService.kt           ← application service
    │   ├── CreateTenantCommand.kt     ← command DTO
    │   ├── RegisterAgentCommand.kt    ← command DTO
    │   └── port/
    │       ├── TenantRepository.kt        ← driven port
    │       ├── AgentRepository.kt         ← driven port
    │       └── DomainEventPublisher.kt    ← driven port (domain events)
    ├── infrastructure/
    └── api/
```

### 12.6 Domain Events (Cross-Context Communication)

Domain events are defined as a sealed interface in the shared kernel (`io.ledge.shared.DomainEvent`). These are the contracts for cross-context communication via Kafka:

| Event | Producer | Consumer(s) | Purpose |
|---|---|---|---|
| `SessionCompleted` | `ingestion` | `memory` (v2) | v1: published but no consumer. v2: triggers snapshot creation when a session ends |
| `TenantPurged` | `tenant` | `ingestion` | GDPR cascade — purge all data for a tenant |

Each event carries typed IDs and an `occurredAt` timestamp.

### 12.7 Value Objects

Domain value objects enforce business rules at construction time. They are implemented as `@JvmInline value class` for zero runtime overhead:

| Value Object | Context | Validation |
|---|---|---|
| `ContextHash` | `ingestion` | Must be valid SHA-256 hex string (64 lowercase hex chars) |
| `SchemaVersion` | `ingestion` | Must be positive integer |
| `Confidence` | `memory` | Must be in range 0.0..1.0 |
| `ContentHash` | `memory` | Must be valid SHA-256 hex string (64 lowercase hex chars) |

### 12.8 Storage-Layer Mapping Conventions

**ClickHouse `context_hash`: empty string as null.** The domain model (`MemoryEvent.contextHash: ContextHash?`) is nullable, but the ClickHouse column is `String` (not `Nullable(String)`). This is a ClickHouse performance best practice — `Nullable` adds a separate column bitmap and disables some optimizations. The adapter layer maps `null` to empty string on write and empty string back to `null` on read. This convention applies only to the ClickHouse storage layer; PostgreSQL columns use standard SQL `NULL` semantics.

### 12.9 Design Rules for Domain Layer

1. **No Spring annotations** — domain layer is pure Kotlin. No `@Entity`, `@Component`, `@Repository`, etc.
2. **Typed IDs everywhere** — `TenantId` not `UUID`. Shared kernel types from `io.ledge.shared`.
3. **Aggregate roots enforce invariants** — `Session.ingest()` checks status. `Tenant.suspend()` checks valid transition. These are not anemic data classes.
4. **Value objects validate on construction** — `Confidence(1.5f)` throws, `ContextHash("")` throws.
5. **Immutable where the domain says immutable** — `MemoryEvent` is a data class with val-only properties. Aggregate roots use var for mutable state they manage (status, sequence counters).

### 12.10 Infrastructure Adapter Patterns

**R2DBC adapters** use `DatabaseClient` with manual SQL (not Spring Data `ReactiveCrudRepository`) because typed IDs and non-data-class aggregates are incompatible with entity mapping. All reactive calls bridge to synchronous via `.block()` — this is intentional for virtual thread compatibility (§1.2).

**ClickHouse adapters** use JDBC `DriverManager` (HTTP protocol). No connection pool in v1 — ClickHouse HTTP JDBC is stateless. The `ClickHouseMemoryEventMapper` (shared object in `ingestion/infrastructure/`) is imported by `memory/infrastructure/` — infrastructure layers permit cross-context dependencies.

**Redis context cache** (`io.ledge.infrastructure.RedisContextCache`) is infrastructure-only with no port interface. It is a cross-cutting concern consumed by both ingestion (Phase 4 Kafka consumer) and memory (Phase 6 query API). Uses `ReactiveStringRedisTemplate` with `.block()` bridging.

**`ClickHouseMemoryEventWriter`** is infrastructure-only (no port interface). Phase 4's Kafka consumer will use it directly to persist events to ClickHouse.

**`ObservationEventQuery`** queries the `memory_events` main table (not the materialized view) because the MV lacks columns needed for full `MemoryEvent` reconstruction (`sequence_number`, `parent_event_id`, `schema_version`).

**Kafka adapters** (`io.ledge.infrastructure.kafka`):
- `KafkaMemoryEventPublisher` implements the `MemoryEventPublisher` port. Serializes `MemoryEvent` → `MemoryEventEnvelope` → JSON, sends to `ledge.events` with key = `tenantId` for partition isolation. Uses synchronous `.get()` on the send future to match the port's synchronous contract.
- `MemoryEventEnvelope` is a JSON-serializable DTO (not a domain object). All typed IDs are flattened to strings. `occurredAt` is ISO-8601. Nullable fields (`contextHash`, `parentEventId`) serialize as JSON null.
- Consumer Group A (`clickhouse-writer`): `ClickHouseWriterConsumer` deserializes envelope → `MemoryEvent`, delegates to `ClickHouseMemoryEventWriter.write()`.
- Consumer Group B (`postgres-redis-writer`): `RedisWriterConsumer` filters for `CONTEXT_ASSEMBLED` events only, writes to `RedisContextCache` (session context + agent latest context). Redis writes are best-effort with try/catch.
- Error handling: `DefaultErrorHandler` with `FixedBackOff(1000ms, 3 retries)` + `DeadLetterPublishingRecoverer` → `ledge.dlq`.
- `LoggingDomainEventPublisher` implements all three bounded context `DomainEventPublisher` ports as a log-only stub (no Kafka topic for domain events in v1).
