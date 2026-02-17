# ledge-sdk-spring-ai

Spring AI auto-capture integration for ledge. Wraps `ChatClient` calls with a `CallAroundAdvisor` that automatically emits the full cognitive lifecycle — context assembly, inference, tool calls, and agent output — without any manual instrumentation.

## Installation

**Gradle (Kotlin DSL)**

```kotlin
implementation("io.ledge:ledge-sdk-spring-ai:0.1.0-SNAPSHOT")
```

**Gradle (Groovy)**

```groovy
implementation 'io.ledge:ledge-sdk-spring-ai:0.1.0-SNAPSHOT'
```

**Maven**

```xml
<dependency>
  <groupId>io.ledge</groupId>
  <artifactId>ledge-sdk-spring-ai</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

> Requires Spring AI `1.0.0-M6+` and Spring Boot `3.2+` on the classpath. `ledge-sdk` is pulled in automatically as a transitive dependency.

## Setup

**1. Start ledge**

```bash
# From the ledge repo
docker compose up
```

**2. Add configuration to `application.yml`**

```yaml
ledge:
  base-url: http://localhost:8080
  api-key: your-api-key
  agent-id: your-agent-uuid
```

That's it. `LedgeAutoConfiguration` picks up these properties and registers `LedgeClient` and `LedgeObservationAdvisor` as beans automatically.

**3. Add the advisor to your `ChatClient`**

```kotlin
@Service
class MyAgentService(
    private val chatClientBuilder: ChatClient.Builder,
    private val ledgeAdvisor: LedgeObservationAdvisor
) {
    private val chatClient = chatClientBuilder
        .defaultAdvisors(ledgeAdvisor)
        .build()

    fun answer(question: String): String {
        return chatClient.prompt()
            .system("You are a helpful support agent.")
            .user(question)
            .call()
            .content()
    }
}
```

Every call to `chatClient` now automatically emits:

1. `CONTEXT_ASSEMBLED` — system prompt + user message, with SHA-256 `contextHash`
2. `INFERENCE_REQUESTED` — model name from `ChatOptions`
3. `INFERENCE_COMPLETED` — response content + token usage
4. `TOOL_INVOKED` — one event per tool call, if any
5. `AGENT_OUTPUT` — final response content

Each call creates its own session in ledge.

## Configuration Reference

| Property | Default | Description |
|---|---|---|
| `ledge.base-url` | `http://localhost:8080` | ledge server URL |
| `ledge.api-key` | — | **Required.** Auto-configuration activates only when this is set. |
| `ledge.agent-id` | — | Agent UUID registered with your ledge tenant |
| `ledge.batching-enabled` | `true` | Buffer events before sending |
| `ledge.batch-size` | `50` | Flush after N events |
| `ledge.flush-interval-ms` | `100` | Flush every N ms |
| `ledge.max-retries` | `3` | Retries on 5xx / 429 |

## Manual Instrumentation

If you need finer control than the advisor provides, inject `LedgeClient` directly:

```kotlin
@Service
class MyAgentService(
    private val ledgeClient: LedgeClient
) {
    fun answer(question: String): String {
        val session = ledgeClient.createSession(agentId = "your-agent-uuid")

        session.userInput(question)
        // ... call model manually ...
        session.agentOutput("the response")

        ledgeClient.completeSession(session.sessionId)
        return "the response"
    }
}
```

See [ledge-sdk README](../ledge-sdk/README.md) for the full manual instrumentation API.

## How Auto-Configuration Works

`LedgeAutoConfiguration` activates when:
- `spring-ai-core` is on the classpath (`ChatClient` class exists)
- `ledge.api-key` property is set

It registers two beans:
- `LedgeClient` — the underlying HTTP client (singleton, closed on application shutdown)
- `LedgeObservationAdvisor` — the advisor to attach to your `ChatClient`

Both beans are `@ConditionalOnMissingBean` — define your own if you need custom configuration.

## Building

```bash
./gradlew :ledge-sdk-spring-ai:build
./gradlew :ledge-sdk-spring-ai:test
```
