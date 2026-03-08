-- ledge PostgreSQL schema — v1: Observation Layer
-- Manages: tenants, agents, sessions (ACID-critical relational state)

CREATE TABLE tenants (
    tenant_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    api_key_hash TEXT NOT NULL UNIQUE,   -- bcrypt hash (renamed from PRD's "api_key")
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
    metadata            JSONB DEFAULT '{}'
);

CREATE INDEX idx_sessions_agent_tenant ON sessions(agent_id, tenant_id);

-- v2: Knowledge Layer (not infrastructure-wired in v1)
-- CREATE TABLE memory_entries_live ( ... );
-- CREATE INDEX idx_memory_entries_agent ON memory_entries_live(agent_id, tenant_id);
