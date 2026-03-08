package io.ledge.sdk

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.ledge.sdk.event.ContentBlock
import io.ledge.sdk.event.EventType
import io.ledge.sdk.event.ObservationEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LedgeClientTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var client: LedgeClient

    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(wireMockConfig().dynamicPort())
        wireMock.start()
        client = LedgeClient(
            LedgeConfig(
                baseUrl = "http://localhost:${wireMock.port()}",
                apiKey = "test-key",
                batchingEnabled = false
            )
        )
    }

    @AfterEach
    fun teardown() {
        client.close()
        wireMock.stop()
    }

    @Test
    fun `createSession calls POST sessions and returns LedgeSession`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/sessions"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {"sessionId":"sess-1","agentId":"agent-1","tenantId":"t-1",
                             "startedAt":"2024-01-01T00:00:00Z","endedAt":null,
                             "status":"ACTIVE"}
                        """.trimIndent())
                )
        )

        val session = client.createSession("agent-1")

        assertEquals("sess-1", session.sessionId)
        assertEquals("agent-1", session.agentId)
        wireMock.verify(
            postRequestedFor(urlEqualTo("/api/v1/sessions"))
                .withHeader("X-API-Key", equalTo("test-key"))
                .withRequestBody(matchingJsonPath("$.agentId", equalTo("agent-1")))
        )
    }

    @Test
    fun `completeSession calls PATCH with COMPLETED status`() {
        wireMock.stubFor(
            patch(urlEqualTo("/api/v1/sessions/sess-1"))
                .willReturn(
                    aResponse()
                        .withStatus(202)
                )
        )

        client.completeSession("sess-1")

        wireMock.verify(
            patchRequestedFor(urlEqualTo("/api/v1/sessions/sess-1"))
                .withRequestBody(matchingJsonPath("$.status", equalTo("COMPLETED")))
        )
    }

    @Test
    fun `emit sends event via POST and returns eventId`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events"))
                .willReturn(
                    aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"eventId":"evt-42"}""")
                )
        )

        val eventId = client.emit(
            ObservationEvent(
                sessionId = "sess-1",
                agentId = "agent-1",
                eventType = EventType.USER_INPUT,
                payload = mapOf("content" to "hello")
            )
        )

        assertEquals("evt-42", eventId)
    }

    @Test
    fun `batching mode enqueues events and returns pending ID`() {
        val batchClient = LedgeClient(
            LedgeConfig(
                baseUrl = "http://localhost:${wireMock.port()}",
                apiKey = "test-key",
                batchingEnabled = true,
                batchSize = 50,
                flushIntervalMs = 10000
            )
        )

        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events/batch"))
                .willReturn(
                    aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"accepted":1,"results":[{"eventId":"evt-1"}]}""")
                )
        )

        val eventId = batchClient.emit(
            ObservationEvent(
                sessionId = "sess-1",
                agentId = "agent-1",
                eventType = EventType.USER_INPUT,
                payload = mapOf("content" to "hello")
            )
        )

        assertTrue(eventId.startsWith("pending-"))
        batchClient.flush()
        Thread.sleep(100)

        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/events/batch")))
        batchClient.close()
    }

    @Test
    fun `contextAssembled auto-computes contextHash`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/sessions"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {"sessionId":"sess-1","agentId":"agent-1","tenantId":"t-1",
                             "startedAt":"2024-01-01T00:00:00Z","endedAt":null,
                             "status":"ACTIVE"}
                        """.trimIndent())
                )
        )
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events"))
                .willReturn(
                    aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"eventId":"evt-ctx"}""")
                )
        )

        val session = client.createSession("agent-1")
        session.contextAssembled(listOf(ContentBlock("system", "You are helpful")))

        wireMock.verify(
            postRequestedFor(urlEqualTo("/api/v1/events"))
                .withRequestBody(matchingJsonPath("$.contextHash"))
                .withRequestBody(matchingJsonPath("$.eventType", equalTo("CONTEXT_ASSEMBLED")))
        )
    }
}
