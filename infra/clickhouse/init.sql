CREATE DATABASE IF NOT EXISTS ledge;

-- All observation events. Append-only. Never updated.
CREATE TABLE IF NOT EXISTS ledge.memory_events (
    event_id        UUID,
    session_id      UUID,
    agent_id        UUID,
    tenant_id       UUID,
    event_type      LowCardinality(String),
    sequence_number Int64,
    occurred_at     DateTime64(3, 'UTC'),
    payload         String,
    context_hash    String,                 -- empty string = null (ClickHouse perf optimization)
    parent_event_id Nullable(UUID),
    schema_version  Int32
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (tenant_id, agent_id, occurred_at, sequence_number);

-- Materialized view: fast CONTEXT_ASSEMBLED point-in-time queries
-- Supports: GET /api/v1/agents/{agentId}/context?at=<timestamp>
-- Target: < 500ms cold query latency (PRD §10.1)
CREATE MATERIALIZED VIEW IF NOT EXISTS ledge.context_assembled_mv
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
FROM ledge.memory_events
WHERE event_type = 'CONTEXT_ASSEMBLED';

-- v2: Knowledge Layer tables (not infrastructure-wired in v1)
-- CREATE TABLE IF NOT EXISTS ledge.memory_snapshots ( ... );
-- CREATE TABLE IF NOT EXISTS ledge.memory_entries ( ... );
