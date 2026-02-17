# ledge

Event-sourced observability infrastructure for AI agents. Captures the full cognitive lifecycle — context windows, inference requests, reasoning traces, tool calls, and responses — as immutable, timestamped records. Exposes APIs for point-in-time context reconstruction, context window diffing, and full session audit trails.

Built for regulated environments where AI-assisted decisions require explainability: finance, legal, healthcare, insurance.

## Architecture

```
AI Agent App
  └── ledge-sdk (instruments agent, emits events)
        └── POST /api/v1/events/batch
              └── Kafka (immutable event log)
                    ├── Consumer A → ClickHouse (columnar audit storage)
                    └── Consumer B → PostgreSQL + Redis (session state + hot cache)
```

**Stack:** Kotlin · Spring Boot · Apache Kafka · ClickHouse · PostgreSQL · Redis · Prometheus · Grafana

## Modules

| Module | Description |
|---|---|
| [`ledge-server`](ledge-server/README.md) | The backend — Spring Boot service, Kafka pipeline, HTTP API |
| [`ledge-sdk`](ledge-sdk/README.md) | JVM client library — zero Spring dependency, works in any JVM app |
| [`ledge-sdk-spring-ai`](ledge-sdk-spring-ai/README.md) | Spring AI integration — auto-captures `ChatClient` calls with no manual instrumentation |

## Quick Start

**1. Start the backend**

```bash
cp .env.example .env       # set POSTGRES_PASSWORD and GRAFANA_PASSWORD
docker compose up
curl http://localhost:8080/actuator/health
```

**2. Install the SDK locally**

```bash
./gradlew :ledge-sdk:publishToMavenLocal
```

**3. Instrument your agent**

```kotlin
val ledge = LedgeClient(LedgeConfig(baseUrl = "http://localhost:8080", apiKey = "your-key"))
val session = ledge.createSession(agentId = "your-agent-uuid")

session.userInput("What is the refund policy?")
session.contextAssembled(listOf(ContentBlock("system", "You are helpful"), ContentBlock("user", "What is the refund policy?")))
val infId = session.inferenceRequested("gpt-4o", "openai")
session.inferenceCompleted("Our policy...", TokenUsage(50, 87, 137), infId)
session.agentOutput("Our policy...", infId)

ledge.completeSession(session.sessionId)
ledge.close()
```

See [`ledge-server/README.md`](ledge-server/README.md) for infrastructure setup, tenant/agent provisioning, and the full API reference.

## Tests

```bash
# All modules
./gradlew test

# Server only
./gradlew :ledge-server:test
./gradlew :ledge-server:integrationTest   # requires Docker

# SDK
./gradlew :ledge-sdk:test
./gradlew :ledge-sdk-spring-ai:test
```

## Build

```bash
./gradlew build
```
