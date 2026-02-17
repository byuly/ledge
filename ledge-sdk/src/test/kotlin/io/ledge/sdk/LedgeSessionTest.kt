package io.ledge.sdk

import io.ledge.sdk.event.EventType
import io.ledge.sdk.event.TokenUsage
import io.ledge.sdk.model.IngestEventRequest
import io.ledge.sdk.model.IngestEventResponse
import io.ledge.sdk.transport.HttpTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class LedgeSessionTest {

    private val capturedEvents = CopyOnWriteArrayList<IngestEventRequest>()
    private var eventCounter = 0
    private lateinit var session: LedgeSession

    @BeforeEach
    fun setup() {
        capturedEvents.clear()
        eventCounter = 0
        val transport = object : HttpTransport {
            @Suppress("UNCHECKED_CAST")
            override fun <T> post(path: String, body: Any, responseType: Class<T>): T {
                if (body is IngestEventRequest) {
                    capturedEvents.add(body)
                }
                return IngestEventResponse("evt-${++eventCounter}", eventCounter.toLong()) as T
            }

            override fun <T> get(path: String, responseType: Class<T>): T = throw UnsupportedOperationException()
            override fun <T> patch(path: String, body: Any, responseType: Class<T>): T = throw UnsupportedOperationException()
            override fun <T> postAsync(path: String, body: Any, responseType: Class<T>): CompletableFuture<T> = CompletableFuture.supplyAsync { post(path, body, responseType) }
            override fun <T> getAsync(path: String, responseType: Class<T>): CompletableFuture<T> = throw UnsupportedOperationException()
            override fun <T> patchAsync(path: String, body: Any, responseType: Class<T>): CompletableFuture<T> = throw UnsupportedOperationException()
        }

        val client = LedgeClient(
            LedgeConfig(
                baseUrl = "http://localhost:9999",
                apiKey = "test-key",
                batchingEnabled = false
            ),
            transport
        )
        session = LedgeSession("sess-1", "agent-1", client)
    }

    @Test
    fun `parentEventId auto-chains across events`() {
        assertNull(session.lastEventId)

        session.userInput("hello")
        assertEquals("evt-1", session.lastEventId)
        assertNull(capturedEvents[0].parentEventId) // first event has no parent

        session.inferenceRequested("gpt-4o", "openai")
        assertEquals("evt-2", session.lastEventId)
        assertEquals("evt-1", capturedEvents[1].parentEventId) // chains to previous

        session.inferenceCompleted("response", TokenUsage(10, 20, 30))
        assertEquals("evt-3", session.lastEventId)
        assertEquals("evt-2", capturedEvents[2].parentEventId)
    }

    @Test
    fun `explicit parentEventId overrides auto-chaining`() {
        session.userInput("hello")
        session.inferenceCompleted("response", TokenUsage(10, 20, 30), parentEventId = "custom-parent")

        assertEquals("custom-parent", capturedEvents[1].parentEventId)
    }

    @Test
    fun `convenience methods set correct event types`() {
        session.userInput("hi")
        assertEquals("USER_INPUT", capturedEvents[0].eventType)

        session.inferenceRequested("gpt-4o", "openai")
        assertEquals("INFERENCE_REQUESTED", capturedEvents[1].eventType)

        session.inferenceCompleted("out", TokenUsage(1, 2, 3))
        assertEquals("INFERENCE_COMPLETED", capturedEvents[2].eventType)

        session.agentOutput("result")
        assertEquals("AGENT_OUTPUT", capturedEvents[3].eventType)

        session.toolInvoked("search", mapOf("q" to "test"))
        assertEquals("TOOL_INVOKED", capturedEvents[4].eventType)

        session.toolResponded("search", "results")
        assertEquals("TOOL_RESPONDED", capturedEvents[5].eventType)

        session.reasoningTrace("thinking...")
        assertEquals("REASONING_TRACE", capturedEvents[6].eventType)

        session.error("something broke", "RuntimeException")
        assertEquals("ERROR", capturedEvents[7].eventType)
    }

    @Test
    fun `all events use correct sessionId and agentId`() {
        session.userInput("test")
        session.agentOutput("output")

        capturedEvents.forEach {
            assertEquals("sess-1", it.sessionId)
            assertEquals("agent-1", it.agentId)
        }
    }
}
