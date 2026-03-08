# ledge-server

The ledge backend. Ingests cognitive lifecycle events from AI agents, stores them in ClickHouse (audit/query) and PostgreSQL (session state), and exposes HTTP APIs for event ingestion, session management, and observation queries.

**Stack:** Kotlin · Spring Boot · Apache Kafka · ClickHouse · PostgreSQL · Redis · Prometheus · Grafana

---

## Prerequisites

- Docker and Docker Compose
- JDK 21 (only needed if running outside Docker)

---

## Infrastructure Setup

**1. Clone and configure environment**

```bash
git clone https://github.com/your-org/ledge
cd ledge
cp .env.example .env
```

Edit `.env` and set:

```bash
POSTGRES_PASSWORD=your_pg_password
GRAFANA_PASSWORD=your_grafana_password
CLICKHOUSE_PASSWORD=your_clickhouse_password

# Compute SHA256 hash of ClickHouse password
CLICKHOUSE_PASSWORD_SHA256_HEX=$(echo -n "your_clickhouse_password" | shasum -a 256 | awk '{print $1}')
```

> **Note:** ClickHouse requires the password hash, not the plaintext. The hash is automatically computed from `CLICKHOUSE_PASSWORD` in the example above.

**2. Start the full stack**

```bash
docker compose up
```

This starts:

| Service | Port | Purpose |
|---|---|---|
| ledge | 8080 | HTTP API |
| PostgreSQL | 5432 | Session state |
| ClickHouse | 8123 (HTTP), 9000 (native) | Event storage |
| Redis | 6379 | API key cache + rate limiting |
| Kafka | 9092 | Event pipeline |
| Prometheus | 9090 | Metrics |
| Grafana | 3000 | Dashboards |

Wait for all services to be healthy (takes ~30 seconds on first run):

```bash
docker compose ps
```

**3. Verify ledge is up**

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## First-Time Setup: Tenant and Agent

All API calls (except `POST /api/v1/tenants`) require an `X-API-Key` header. Here's how to get one.

**1. Pick an API key and compute its SHA-256 hash**

```bash
echo -n "my-secret-key" | sha256sum
# e.g. 7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d906 my-secret-key
```

Or in Python:

```python
import hashlib
hashlib.sha256(b"my-secret-key").hexdigest()
```

**2. Create a tenant** (no auth required)

```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-org",
    "apiKeyHash": "7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d906"
  }'
```

Response:

```json
{
  "tenantId": "01234567-...",
  "name": "my-org",
  "status": "ACTIVE",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

Save the `tenantId`.

**3. Register an agent**

```bash
curl -X POST http://localhost:8080/api/v1/agents \
  -H "Content-Type: application/json" \
  -H "X-API-Key: my-secret-key" \
  -d '{
    "name": "support-agent",
    "description": "Customer support AI agent",
    "metadata": {"team": "support", "env": "prod"}
  }'
```

Response:

```json
{
  "agentId": "98765432-...",
  "tenantId": "01234567-...",
  "name": "support-agent",
  "description": "Customer support AI agent",
  "metadata": {"team": "support", "env": "prod"},
  "createdAt": "2024-01-01T00:00:00Z"
}
```

Save the `agentId`. You now have everything needed to start sending events.

---

## API

All write endpoints require `X-API-Key`. The server resolves the tenant from the key and injects `X-Tenant-Id` internally — you never set that header yourself.

### Sessions

**Start a session**

```bash
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: my-secret-key" \
  -d '{"agentId": "98765432-..."}'
```

```json
{
  "sessionId": "aabbccdd-...",
  "agentId": "98765432-...",
  "tenantId": "01234567-...",
  "startedAt": "2024-01-01T00:00:00Z",
  "endedAt": null,
  "status": "ACTIVE"
}
```

**End a session**

```bash
# Complete
curl -X PATCH http://localhost:8080/api/v1/sessions/aabbccdd-... \
  -H "Content-Type: application/json" \
  -H "X-API-Key: my-secret-key" \
  -d '{"status": "COMPLETED"}'

# Abandon
curl -X PATCH http://localhost:8080/api/v1/sessions/aabbccdd-... \
  -H "Content-Type: application/json" \
  -H "X-API-Key: my-secret-key" \
  -d '{"status": "ABANDONED"}'
```

**Get session**

```bash
curl http://localhost:8080/api/v1/sessions/aabbccdd-... \
  -H "X-API-Key: my-secret-key"
```

**List events in a session**

```bash
curl "http://localhost:8080/api/v1/sessions/aabbccdd-.../events?limit=50" \
  -H "X-API-Key: my-secret-key"
```

### Events

**Ingest a single event**

```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -H "X-API-Key: my-secret-key" \
  -d '{
    "sessionId": "aabbccdd-...",
    "agentId": "98765432-...",
    "eventType": "USER_INPUT",
    "occurredAt": "2024-01-01T00:00:00Z",
    "payload": {"content": "What is the refund policy?"},
    "schemaVersion": 1
  }'
```

```json
{"eventId": "evt-uuid"}
```

**Ingest a batch** (up to 500 events)

```bash
curl -X POST http://localhost:8080/api/v1/events/batch \
  -H "Content-Type: application/json" \
  -H "X-API-Key: my-secret-key" \
  -d '{
    "events": [
      {
        "sessionId": "aabbccdd-...",
        "agentId": "98765432-...",
        "eventType": "CONTEXT_ASSEMBLED",
        "occurredAt": "2024-01-01T00:00:00Z",
        "payload": {"blocks": [{"role": "system", "content": "You are helpful"}]},
        "contextHash": "e3b0c44298fc1c149afbf4c8996fb924...",
        "schemaVersion": 1
      },
      {
        "sessionId": "aabbccdd-...",
        "agentId": "98765432-...",
        "eventType": "INFERENCE_REQUESTED",
        "occurredAt": "2024-01-01T00:00:01Z",
        "payload": {"model": "gpt-4o", "provider": "openai"},
        "schemaVersion": 1
      }
    ]
  }'
```

```json
{"accepted": 2, "results": [...]}
```

**Event types:** `USER_INPUT`, `CONTEXT_ASSEMBLED`, `INFERENCE_REQUESTED`, `INFERENCE_COMPLETED`, `REASONING_TRACE`, `AGENT_OUTPUT`, `TOOL_INVOKED`, `TOOL_RESPONDED`, `ERROR`

### Observation Queries

**Point-in-time context window**

```bash
curl "http://localhost:8080/api/v1/agents/98765432-.../context?at=2024-01-01T00:00:30Z" \
  -H "X-API-Key: my-secret-key"
```

Returns the last `CONTEXT_ASSEMBLED` event before the given timestamp.

**Context diff between two timestamps**

```bash
curl "http://localhost:8080/api/v1/agents/98765432-.../context/diff?from=2024-01-01T00:00:00Z&to=2024-01-01T01:00:00Z" \
  -H "X-API-Key: my-secret-key"
```

Returns added/removed/changed blocks between the two context windows.

**Full session audit trail**

```bash
curl "http://localhost:8080/api/v1/sessions/aabbccdd-.../audit" \
  -H "X-API-Key: my-secret-key"
```

Returns every event in the session in sequence order.

---

## Auth

Every request (except `POST /api/v1/tenants`, `DELETE /api/v1/tenants/:id`, and `/actuator/*`) must include:

```
X-API-Key: your-raw-key
```

The server hashes it with SHA-256, looks up the tenant in PostgreSQL, checks the tenant isn't suspended/deleted, then enforces a rate limit (default 1000 requests/minute per tenant) via Redis.

---

## Running Locally (Without Docker)

Requires a running PostgreSQL, ClickHouse, Redis, and Kafka. Then:

```bash
./gradlew :ledge-server:bootRun --args='--spring.profiles.active=local'
```

With environment variables:

```bash
POSTGRES_PASSWORD=yourpassword ./gradlew :ledge-server:bootRun --args='--spring.profiles.active=local'
```

---

## Tests

**Unit tests**

```bash
./gradlew :ledge-server:test
```

**Integration tests** (requires Docker — Testcontainers spins up PostgreSQL, ClickHouse, Redis, Kafka)

```bash
./gradlew :ledge-server:integrationTest
```

---

## Observability

- **Health:** `GET /actuator/health`
- **Prometheus metrics:** `GET /actuator/prometheus`
- **Grafana:** `http://localhost:3000` (login: `admin` / your `GRAFANA_PASSWORD`)

---

## Build

```bash
# Compile
./gradlew :ledge-server:compileKotlin

# Runnable JAR
./gradlew :ledge-server:bootJar
java -jar ledge-server/build/libs/ledge-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=local

# Docker image
docker build -t ledge-server .
```
