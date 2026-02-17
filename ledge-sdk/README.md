# ledge-sdk

JVM client library for instrumenting AI agents with cognitive lifecycle observability. Sends observation events to a ledge server — no Spring dependency, works in any JVM application.

## Installation

Not yet published to Maven Central. Install locally from source:

```bash
git clone https://github.com/your-org/ledge
cd ledge
./gradlew :ledge-sdk:publishToMavenLocal
```

Then add to your project:

**Gradle (Kotlin DSL)**

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.ledge:ledge-sdk:0.1.0-SNAPSHOT")
}
```

**Gradle (Groovy)**

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'io.ledge:ledge-sdk:0.1.0-SNAPSHOT'
}
```

**Maven**

```xml
<dependency>
  <groupId>io.ledge</groupId>
  <artifactId>ledge-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

> Requires JVM 17+. No Spring, no Kafka, no database dependencies pulled in.

## Setup

You need a running ledge server and an API key. If you're running locally:

```bash
docker compose up   # from the ledge repo root
```

Then create a tenant and get an API key:

```bash
# Create a tenant
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{"name": "my-org"}'

# Register an agent
curl -X POST http://localhost:8080/api/v1/agents \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <your-api-key>" \
  -d '{"name": "my-agent"}'
```

## Quick Start

```kotlin
import io.ledge.sdk.LedgeClient
import io.ledge.sdk.LedgeConfig
import io.ledge.sdk.event.ContentBlock
import io.ledge.sdk.event.TokenUsage

val ledge = LedgeClient(
    LedgeConfig(
        baseUrl = "http://localhost:8080",
        apiKey  = "your-api-key"
    )
)

val session = ledge.createSession(agentId = "your-agent-uuid")

// Instrument your agent's cognitive lifecycle
session.userInput("What is the refund policy?")

session.contextAssembled(listOf(
    ContentBlock(role = "system", content = "You are a helpful support agent."),
    ContentBlock(role = "user",   content = "What is the refund policy?")
))

val inferenceId = session.inferenceRequested(model = "gpt-4o", provider = "openai")

// ... call your model ...

session.inferenceCompleted(
    content    = "Our refund policy allows returns within 30 days.",
    tokenUsage = TokenUsage(promptTokens = 50, completionTokens = 87, totalTokens = 137),
    parentEventId = inferenceId
)

session.agentOutput(
    content       = "Our refund policy allows returns within 30 days.",
    parentEventId = inferenceId
)

ledge.completeSession(session.sessionId)
ledge.close()
```

## Event Types

| Method | Event |
|---|---|
| `session.userInput(content)` | `USER_INPUT` |
| `session.contextAssembled(blocks)` | `CONTEXT_ASSEMBLED` — auto-computes SHA-256 `contextHash` |
| `session.inferenceRequested(model, provider)` | `INFERENCE_REQUESTED` |
| `session.inferenceCompleted(content, usage)` | `INFERENCE_COMPLETED` |
| `session.reasoningTrace(trace)` | `REASONING_TRACE` |
| `session.agentOutput(content)` | `AGENT_OUTPUT` |
| `session.toolInvoked(name, arguments)` | `TOOL_INVOKED` |
| `session.toolResponded(name, result)` | `TOOL_RESPONDED` |
| `session.error(message, type)` | `ERROR` |

All methods return the emitted event's ID. Each event automatically chains `parentEventId` to the previous event in the session — pass an explicit `parentEventId` to override.

## Configuration

```kotlin
LedgeConfig(
    baseUrl          = "http://localhost:8080",  // ledge server URL
    apiKey           = "your-api-key",           // required
    batchingEnabled  = true,                     // default: true
    batchSize        = 50,                       // flush after N events
    flushIntervalMs  = 100,                      // flush every N ms
    maxRetries       = 3,                        // retries on 5xx / 429
    connectTimeoutMs = 5000,
    requestTimeoutMs = 10000
)
```

Batching is on by default. Events are flushed every 100ms or when 50 accumulate, whichever comes first. Failed sends are retried with exponential backoff (100ms × 2^attempt + jitter).

To disable batching and send every event immediately:

```kotlin
LedgeConfig(baseUrl = "...", apiKey = "...", batchingEnabled = false)
```

## Async Client

A coroutine-based async client is available if you bring `kotlinx-coroutines-core` as a runtime dependency:

```kotlin
val ledge = LedgeAsyncClient(LedgeConfig(baseUrl = "...", apiKey = "..."))

val session = ledge.createSession(agentId = "your-agent-uuid")  // suspend fun
```

The async client has the same API surface as `LedgeClient`, with all methods as `suspend` functions.

## Closing

Always call `close()` (or use `use {}`) when done. This flushes any buffered events and shuts down the background scheduler:

```kotlin
ledge.use {
    val session = it.createSession(agentId = "...")
    // ... instrument ...
    it.completeSession(session.sessionId)
}
```

## Building

```bash
./gradlew :ledge-sdk:build
./gradlew :ledge-sdk:test
```
