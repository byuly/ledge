# ledge

Event-sourced observability infrastructure for AI agents. Captures the full cognitive lifecycle — context windows, inference requests, reasoning traces, tool calls, and responses — as immutable, timestamped records. Exposes APIs for point-in-time context reconstruction, context window diffing, and full session audit trails.

Built for regulated environments where AI-assisted decisions require explainability: finance, legal, healthcare, insurance.

## Architecture

```
AI Agent App
  └── ledge SDK (instruments agent, emits events)
        └── POST /api/v1/events
              └── Kafka (immutable event log)
                    ├── Consumer A → ClickHouse (columnar audit storage)
                    └── Consumer B → PostgreSQL + Redis (session state + hot cache)
```

**Stack:** Kotlin · Spring Boot · Apache Kafka · ClickHouse · PostgreSQL · Redis · Prometheus · Grafana

## Getting Started

**1. Configure environment**

```bash
cp .env.example .env
# Edit .env — set POSTGRES_PASSWORD and GRAFANA_PASSWORD
```

**2. Start the full stack**

```bash
docker compose up
```

This starts: `ledge` (port 8080), Kafka, PostgreSQL, ClickHouse, Redis, Prometheus (port 9090), Grafana (port 3000).

**3. Verify**

```bash
curl http://localhost:8080/actuator/health
```

## Tests

**Unit tests**

```bash
./gradlew test
```

**Integration tests** (requires Docker — spins up ClickHouse, PostgreSQL, Redis via Testcontainers)

```bash
./gradlew integrationTest
```

## API

| Endpoint | Description |
|---|---|
| `POST /api/v1/events` | Ingest a single observation event |
| `POST /api/v1/events/batch` | Ingest up to 500 events |
| `POST /api/v1/sessions` | Start a new session |
| `GET /api/v1/agents/{agentId}/context?at=` | Point-in-time context window reconstruction |
| `GET /api/v1/agents/{agentId}/context/diff?from=&to=` | Context window diff between two timestamps |
| `GET /api/v1/sessions/{sessionId}/audit` | Full cognitive trace for a session |
