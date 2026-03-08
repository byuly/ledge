# Ledge TODO — PRD v2.0 Discrepancy Tracker

Ordered by priority. Each item is self-contained and independently actionable.

---

## High Priority

### [x] 1. Implement payload validation per EventType

**PRD §4.2 / §5.7:** "Ingest API validates payload structure per EventType"
**PRD v1 checklist §9.1:** `[ ] Payload validation per EventType (structured JSON schemas — see §5.7)` — still unchecked.
**Current code:** No per-EventType payload validation confirmed in `IngestionService` or `EventController`.

**What the PRD requires:**
- `CONTEXT_ASSEMBLED` → must contain `blocks[]`
- `INFERENCE_REQUESTED` → must contain `modelId`, `provider`
- Other EventTypes have analogous required fields per §5.7

**Files to touch:**
- `ledge-server/src/main/kotlin/io/ledge/ingestion/application/IngestionService.kt` — add validation logic
- `ledge-server/src/main/kotlin/io/ledge/ingestion/interfaces/EventController.kt` — return 400 on validation failure

**Action:** Verify whether validation exists. If absent, implement schema checks per PRD §5.7 and return `400 Bad Request` with a descriptive error body.

---

### [x] 2. Instrument domain metrics (Micrometer)

**PRD §10.4:** Specifies named Micrometer counters/timers that must be manually instrumented:

| Metric | Type | Tags |
|--------|------|------|
| `ledge.events.ingested` | Counter | `eventType`, `tenantId` |
| `ledge.clickhouse.write.duration` | Timer | — |
| `ledge.redis.cache.requests` | Counter | `result` (hit/miss) |
| `ledge.query.pointintime.duration` | Timer | `source` (redis/clickhouse) |
| `ledge.query.diff.duration` | Timer | — |
| `ledge.sessions.active` | Gauge | — |

**Current code:** Metrics instrumentation not confirmed present. PRD v1 checklist has no checkbox for it, suggesting it was likely skipped.

**Files to touch:**
- `ledge-server/src/main/kotlin/io/ledge/ingestion/application/IngestionService.kt` — `ledge.events.ingested`, `ledge.clickhouse.write.duration`
- `ledge-server/src/main/kotlin/io/ledge/infrastructure/cache/RedisContextCache.kt` — `ledge.redis.cache.requests`
- `ledge-server/src/main/kotlin/io/ledge/memory/application/MemoryService.kt` — `ledge.query.pointintime.duration`, `ledge.query.diff.duration`
- Some gauge registration for `ledge.sessions.active` on a scheduled basis or via repository count

**Action:** Inject `MeterRegistry` into relevant services and add instrumentation at the appropriate call sites.

---

### [x] 3. Resolve PRD vs code mismatch: Consumer Group B PostgreSQL writes — DONE

**Resolved:** Implemented Option B (Kafka-first). Event ingestion no longer writes to PostgreSQL synchronously — only reads for session validation. Session lifecycle transitions (complete/abandon) flow through Kafka as `SESSION_COMPLETED`/`SESSION_ABANDONED` events, consumed by `RedisWriterConsumer` which updates PostgreSQL. Sequence numbers removed entirely; event ordering uses `occurredAt` timestamps. PRD updated to match.

---

## Medium Priority

### [ ] 4. Trace and fix `session:{id}:status` Redis key — never populated via Kafka

**PRD §8.3 Redis Key Design:** `session:{sessionId}:status` listed as a valid key.
**`RedisContextCache.kt`:** Has `putSessionStatus()` and `getSessionStatus()` methods (L38–48).
**`RedisWriterConsumer.kt`:** Does NOT call `putSessionStatus()` — only caches `CONTEXT_ASSEMBLED` payloads.

**Files to touch:**
- `ledge-server/src/main/kotlin/io/ledge/infrastructure/kafka/RedisWriterConsumer.kt` — call `putSessionStatus()` where appropriate
- Search codebase for all callers of `putSessionStatus()` to confirm it is truly never called

**Action:** Grep for `putSessionStatus` across the codebase. If it has no callers, decide: populate it in the Kafka consumer, or remove it from `RedisContextCache` if unused.

---

### [ ] 5. Update PRD: resolve Kafka partition Open Question #6

**PRD §11 Open Question #6:** "Candidate: 3 partitions per topic for local dev" — marked Open.
**Actual `KafkaTopicConfig.kt` L12:** `NewTopic(EVENTS_TOPIC, 6, 1.toShort())` — 6 partitions.

**Files to touch:**
- `ledge_prd.md` §11 — mark Q6 as resolved, document decision: 6 partitions chosen (rationale: allows up to 6 parallel consumers per group without rebalancing overhead; revisit for production sizing)

---

## Low Priority

### [ ] 6. Fix stale `bcrypt` comment in `infra/sql/init.sql`

**File:** `infra/sql/init.sql` L7
**Current comment:** `-- bcrypt hash (renamed from PRD's "api_key")`
**PRD reality:** Open Question #12 explicitly decided: bcrypt → SHA-256. The comment is factually wrong.

**Risk:** A new contributor could implement bcrypt for API key hashing based on this comment, introducing a bug (SHA-256 hash stored, bcrypt comparison used → all auth fails).

**Fix:** Change to:
```sql
-- SHA-256 hash (deterministic, O(1) index lookup — see PRD §11 Q12)
```

**Files to touch:**
- `infra/sql/init.sql` L7

---

### [ ] 7. Tick off completed v1 checklist items in PRD

**PRD §9.1** still shows these unchecked despite evidence they are done:
- `[ ] Basic README with integration guide (30-minute onboarding)` — READMEs exist in all four modules (`ledge-sdk/README.md`, `ledge-sdk-spring-ai/README.md`, `ledge-server/README.md`, root `README.md`)

**Files to touch:**
- `ledge_prd.md` §9.1 — mark README item as `[x]`; also audit other checklist items against current codebase state and check off anything confirmed done

---

## Deferred / Known Gaps (no action needed now)

### Redis query-path cache read — intentionally deferred

**PRD §4.4:** "check Redis → cache hit for latest CONTEXT_ASSEMBLED? return if timestamp matches"
**Current code:** `ObservationController.getContext()` goes directly to ClickHouse; Redis is populated but never read on the query path.
**PRD Open Question #11:** Explicitly documents this as a follow-up performance enhancement, not a correctness requirement.

**Action:** None now. Revisit as a performance task after v1 is stable. When implementing, modify `MemoryService.getContextAtTime()` to check `RedisContextCache.getLatestContext()` first and fall back to ClickHouse.
