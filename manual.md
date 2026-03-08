# Ledge — Local Stack Manual Testing Guide

Quick reference for bringing up the full stack, exercising the API, and verifying
metrics flow end-to-end through Prometheus and Grafana.

---

## Credentials at a glance

All secrets live in `.env` (not committed). Current dev values:

| Service    | Detail                          | Value                    |
|------------|---------------------------------|--------------------------|
| PostgreSQL | user / password                 | `ml_user` / `dev_postgres_pass` |
| ClickHouse | password                        | `dev_clickhouse_pass`    |
| Grafana    | admin / password                | `admin` / `dev_grafana_pass` |
| Redis      | no auth                         | —                        |
| Kafka      | no auth                         | —                        |

---

## 1. Build and start the stack

### First time / after code changes

The Docker image is built inside the container using a multi-stage Dockerfile.
The build step (`./gradlew :ledge-server:bootJar`) can fail inside Docker if the
JVM heap is too small. The reliable workaround is to build locally and copy the
jar in.

```bash
# Build the fat jar locally (fast, uses local Gradle cache)
./gradlew :ledge-server:bootJar -q

# Start all infrastructure services except ledge itself
docker compose up -d kafka postgres redis clickhouse prometheus grafana

# Wait ~10s for infra to be healthy, then start the app
docker compose up -d ledge

# Copy the newly built jar into the running container and restart it
docker cp ledge-server/build/libs/ledge-server-0.1.0-SNAPSHOT.jar \
    ledge-ledge-1:/app/app.jar
docker compose restart ledge
```

### Subsequent starts (no code changes)

```bash
docker compose up -d
```

### Verify everything is up

```bash
docker compose ps
curl -s localhost:8080/actuator/health   # expect {"status":"UP"}
```

---

## 2. Ports

| Service    | URL / address            |
|------------|--------------------------|
| Ledge API  | http://localhost:8080    |
| Prometheus | http://localhost:9090    |
| Grafana    | http://localhost:3000    |
| PostgreSQL | localhost:5432           |
| ClickHouse | http://localhost:8123    |
| Redis      | localhost:6379           |
| Kafka      | localhost:9092           |

---

## 3. Create a tenant and get an API key

The Ledge API is protected by `X-API-Key`. The key is never stored — only its
SHA-256 hash. You must hash your own key and register that hash.

```bash
# Pick any string as your raw API key
RAW_KEY="my-dev-key"

# Hash it
KEY_HASH=$(echo -n "$RAW_KEY" | shasum -a 256 | awk '{print $1}')

# Register the tenant (no auth required for this endpoint)
curl -s -X POST localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"dev\", \"apiKeyHash\": \"$KEY_HASH\"}" | python3 -m json.tool
```

Save the `tenantId` from the response — you'll need it for direct DB queries
(the API header `X-Tenant-Id` is injected automatically from your API key).

---

## 4. Register an agent

Sessions require a pre-registered agent (FK constraint in PostgreSQL).

```bash
RAW_KEY="my-dev-key"
TENANT_ID="<tenantId from step 3>"

curl -s -X POST localhost:8080/api/v1/agents \
  -H "X-API-Key: $RAW_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Content-Type: application/json" \
  -d '{"name": "dev-agent", "description": "local testing"}' | python3 -m json.tool
```

Save the `agentId`.

---

## 5. Create a session

```bash
RAW_KEY="my-dev-key"
AGENT_ID="<agentId from step 4>"

curl -s -X POST localhost:8080/api/v1/sessions \
  -H "X-API-Key: $RAW_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"agentId\": \"$AGENT_ID\"}" | python3 -m json.tool
```

Save the `sessionId`.

---

## 6. Ingest events

Every event type has required payload fields — wrong fields return `400`.

### Payload schemas (required fields per EventType)

| EventType             | Required fields |
|-----------------------|-----------------|
| `USER_INPUT`          | `content` (str), `inputType` (str) |
| `AGENT_OUTPUT`        | `content` (str), `outputType` (str), `inferenceEventId` (str) |
| `INFERENCE_REQUESTED` | `modelId` (str), `provider` (str) |
| `INFERENCE_COMPLETED` | `response` (str), `finishReason` (str), `latencyMs` (num), `modelId` (str), `usage.promptTokens` (num), `usage.completionTokens` (num) |
| `REASONING_TRACE`     | `thinkingContent` (str), `thinkingTokenCount` (num) |
| `TOOL_INVOKED`        | `toolName` (str), `toolId` (str), `parameters` (obj) |
| `TOOL_RESPONDED`      | `toolName` (str), `toolInvokedEventId` (str), `result` (str), `durationMs` (num) |
| `ERROR`               | `errorType` (str), `message` (str), `recoverable` (bool) |
| `CONTEXT_ASSEMBLED`   | `blocks` (array), `totalTokens` (num) |
| `SESSION_COMPLETED`   | no validation (internal) |
| `SESSION_ABANDONED`   | no validation (internal) |

### Single event

```bash
RAW_KEY="my-dev-key"
SESSION_ID="<sessionId from step 5>"
AGENT_ID="<agentId from step 4>"

curl -s -X POST localhost:8080/api/v1/events \
  -H "X-API-Key: $RAW_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"sessionId\": \"$SESSION_ID\",
    \"agentId\": \"$AGENT_ID\",
    \"eventType\": \"USER_INPUT\",
    \"occurredAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
    \"payload\": {\"content\": \"hello\", \"inputType\": \"text\"}
  }" | python3 -m json.tool
```

### Batch (up to 500 events)

```bash
curl -s -X POST localhost:8080/api/v1/events/batch \
  -H "X-API-Key: $RAW_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"events\": [
    {\"sessionId\": \"$SESSION_ID\", \"agentId\": \"$AGENT_ID\",
     \"eventType\": \"USER_INPUT\",
     \"occurredAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
     \"payload\": {\"content\": \"msg1\", \"inputType\": \"text\"}},
    {\"sessionId\": \"$SESSION_ID\", \"agentId\": \"$AGENT_ID\",
     \"eventType\": \"INFERENCE_REQUESTED\",
     \"occurredAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
     \"payload\": {\"modelId\": \"claude-3-5-sonnet\", \"provider\": \"anthropic\"}}
  ]}" | python3 -m json.tool
```

---

## 7. Check custom metrics

### From the app directly

```bash
# All 6 custom ledge metrics
curl -s localhost:8080/actuator/prometheus | grep -E "^ledge_"
```

Expected output once events have been ingested:
```
ledge_events_ingested_total{...,eventType="USER_INPUT",...} 3.0
ledge_sessions_active{...} 1.0
ledge_clickhouse_write_duration_seconds_count{...} 3.0
ledge_redis_cache_requests_total{...,result="miss",...} 0.0
ledge_query_pointintime_duration_seconds_count{...} 0.0
ledge_query_diff_duration_seconds_count{...} 0.0
```

> Counters and timers only appear in the output **after their first use**.
> `ledge_sessions_active` is always present (registered at startup).

### From Prometheus

```bash
# Check the scrape target is UP
curl -s "localhost:9090/api/v1/targets" | \
  python3 -c "import json,sys; [print(t['labels']['job'], t['health']) \
  for t in json.load(sys.stdin)['data']['activeTargets']]"
# expect: ledge up

# Query a specific metric (after one 15s scrape cycle)
curl -s "localhost:9090/api/v1/query?query=ledge_events_ingested_total" | \
  python3 -m json.tool
```

Open http://localhost:9090 in a browser → use the **Graph** tab → query
`ledge_events_ingested_total` or any other `ledge_*` name.

### From Grafana

1. Open http://localhost:3000
2. Login: `admin` / `dev_grafana_pass`
3. Go to **Dashboards → Ledge Operations**

The dashboard is auto-provisioned at startup via
`observability/grafana/provisioning/dashboards/ledge-operations.json`.

To verify provisioning via API:
```bash
curl -s -u admin:dev_grafana_pass \
  "localhost:3000/api/dashboards/uid/ledge-operations" | \
  python3 -c "import json,sys; d=json.load(sys.stdin); \
  print(d['dashboard']['title'], '|', len(d['dashboard']['panels']), 'panels')"
# expect: Ledge Operations | 6 panels
```

---

## 8. Check PostgreSQL

```bash
# Connect
docker exec -it ledge-postgres-1 psql -U ml_user ledge

# Or run a one-liner
docker exec ledge-postgres-1 psql -U ml_user ledge -c "<SQL>"
```

Useful queries:
```sql
-- All tenants
SELECT tenant_id, name, status, created_at FROM tenants;

-- Active sessions
SELECT session_id, agent_id, tenant_id, status, started_at FROM sessions WHERE status = 'ACTIVE';

-- Count sessions by status
SELECT status, COUNT(*) FROM sessions GROUP BY status;

-- All agents for a tenant
SELECT agent_id, name, created_at FROM agents WHERE tenant_id = '<uuid>';
```

---

## 9. Check ClickHouse

```bash
# Connect via HTTP (simplest)
curl -s "http://localhost:8123/" --data "SELECT count() FROM ledge.memory_events"

# With password if set
curl -s "http://localhost:8123/?user=default&password=dev_clickhouse_pass" \
  --data "SELECT count() FROM ledge.memory_events"

# Connect via CLI
docker exec -it ledge-clickhouse-1 clickhouse-client --password dev_clickhouse_pass
```

Useful queries:
```sql
-- Event count by type
SELECT event_type, count() FROM ledge.memory_events GROUP BY event_type;

-- Latest 10 events
SELECT event_id, session_id, event_type, occurred_at
FROM ledge.memory_events
ORDER BY occurred_at DESC
LIMIT 10;

-- Events for a specific agent
SELECT event_id, event_type, occurred_at, payload
FROM ledge.memory_events
WHERE agent_id = '<uuid>'
ORDER BY occurred_at;
```

---

## 10. Check Redis

```bash
# Connect
docker exec -it ledge-redis-1 redis-cli

# List all keys
KEYS *

# Get a session context
GET session:<sessionId>:context

# Get agent latest context
GET agent:<agentId>:context:latest

# Check a rate limit counter
GET tenant:<tenantId>:ratelimit
```

---

## 11. Check Kafka

```bash
# List topics
docker exec ledge-kafka-1 kafka-topics \
  --bootstrap-server localhost:9092 --list

# Consume from the events topic (from beginning, Ctrl-C to stop)
docker exec ledge-kafka-1 kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic ledge_events \
  --from-beginning \
  --max-messages 5

# Check consumer group lag
docker exec ledge-kafka-1 kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --all-groups
```

---

## 12. Tear down

```bash
# Stop all containers, keep volumes (data survives)
docker compose down

# Stop and delete all volumes (full reset)
docker compose down -v
```

---

## Troubleshooting

**`sessions_agent_id_fkey` constraint error**
You tried to create a session with an agent UUID that isn't registered. Run step 4 first.

**`400 Payload for X is missing required field Y`**
The payload validation from TODO #1 is active. Check the schema table in section 6.

**`docker compose build ledge` fails**
The Dockerfile runs Gradle inside Docker with `-Xmx512m` — it can OOM. Use the
workaround in section 1: build locally with `./gradlew :ledge-server:bootJar -q`,
then `docker cp` the jar.

**Custom `ledge_*` metrics missing from `/actuator/prometheus`**
Counters and timers are lazy — they don't appear until triggered at least once.
Ingest an event (section 6) and re-check. `ledge_sessions_active` should always
appear since it's a gauge registered at startup.

**Prometheus shows no data for `ledge_*` queries**
Prometheus scrapes every 15 seconds. Wait one scrape cycle after ingesting events,
then query again.

**Grafana dashboard is blank / no datasource**
The Prometheus datasource is provisioned automatically from
`observability/grafana/provisioning/datasources/prometheus.yml`. If it's missing,
restart Grafana: `docker compose restart grafana`.
