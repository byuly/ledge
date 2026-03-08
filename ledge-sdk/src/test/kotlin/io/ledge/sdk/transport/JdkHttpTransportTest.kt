package io.ledge.sdk.transport

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.ledge.sdk.LedgeConfig
import io.ledge.sdk.model.IngestEventRequest
import io.ledge.sdk.model.IngestEventResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JdkHttpTransportTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var transport: JdkHttpTransport

    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(wireMockConfig().dynamicPort())
        wireMock.start()
        transport = JdkHttpTransport(
            LedgeConfig(
                baseUrl = "http://localhost:${wireMock.port()}",
                apiKey = "test-api-key",
                maxRetries = 0
            )
        )
    }

    @AfterEach
    fun teardown() {
        wireMock.stop()
    }

    @Test
    fun `sends X-API-Key header`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events"))
                .willReturn(
                    aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"eventId":"evt-1"}""")
                )
        )

        val request = IngestEventRequest(
            sessionId = "sess-1",
            agentId = "agent-1",
            eventType = "USER_INPUT",
            occurredAt = "2024-01-01T00:00:00Z",
            payload = mapOf("content" to "hello")
        )
        transport.post("/api/v1/events", request, IngestEventResponse::class.java)

        wireMock.verify(
            postRequestedFor(urlEqualTo("/api/v1/events"))
                .withHeader("X-API-Key", equalTo("test-api-key"))
                .withHeader("Content-Type", equalTo("application/json"))
        )
    }

    @Test
    fun `deserializes 202 response`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events"))
                .willReturn(
                    aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"eventId":"evt-123"}""")
                )
        )

        val response = transport.post(
            "/api/v1/events",
            IngestEventRequest("s", "a", "USER_INPUT", "2024-01-01T00:00:00Z", emptyMap()),
            IngestEventResponse::class.java
        )

        assertEquals("evt-123", response.eventId)
    }

    @Test
    fun `throws LedgeApiException on 400`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events"))
                .willReturn(aResponse().withStatus(400).withBody("Bad Request"))
        )

        val ex = assertThrows<LedgeApiException> {
            transport.post(
                "/api/v1/events",
                IngestEventRequest("s", "a", "USER_INPUT", "2024-01-01T00:00:00Z", emptyMap()),
                IngestEventResponse::class.java
            )
        }
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun `throws RetryableException on 503`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events"))
                .willReturn(aResponse().withStatus(503).withBody("Service Unavailable"))
        )

        assertThrows<RetryableException> {
            transport.post(
                "/api/v1/events",
                IngestEventRequest("s", "a", "USER_INPUT", "2024-01-01T00:00:00Z", emptyMap()),
                IngestEventResponse::class.java
            )
        }
    }

    @Test
    fun `throws RetryableException on 429`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events"))
                .willReturn(aResponse().withStatus(429).withBody("Rate limited"))
        )

        assertThrows<RetryableException> {
            transport.post(
                "/api/v1/events",
                IngestEventRequest("s", "a", "USER_INPUT", "2024-01-01T00:00:00Z", emptyMap()),
                IngestEventResponse::class.java
            )
        }
    }

    @Test
    fun `retries on 5xx when retry is enabled`() {
        val retryTransport = JdkHttpTransport(
            LedgeConfig(
                baseUrl = "http://localhost:${wireMock.port()}",
                apiKey = "test-api-key",
                maxRetries = 2
            ),
            retryPolicy = RetryPolicy(maxRetries = 2, baseDelayMs = 10)
        )

        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events"))
                .inScenario("retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503).withBody("Unavailable"))
                .willSetStateTo("first-retry")
        )
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events"))
                .inScenario("retry")
                .whenScenarioStateIs("first-retry")
                .willReturn(
                    aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"eventId":"evt-1"}""")
                )
        )

        val response = retryTransport.post(
            "/api/v1/events",
            IngestEventRequest("s", "a", "USER_INPUT", "2024-01-01T00:00:00Z", emptyMap()),
            IngestEventResponse::class.java
        )

        assertEquals("evt-1", response.eventId)
        wireMock.verify(2, postRequestedFor(urlEqualTo("/api/v1/events")))
    }

    @Test
    fun `serializes request body as JSON`() {
        wireMock.stubFor(
            post(urlEqualTo("/api/v1/events"))
                .willReturn(
                    aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"eventId":"evt-1"}""")
                )
        )

        val request = IngestEventRequest(
            sessionId = "sess-1",
            agentId = "agent-1",
            eventType = "USER_INPUT",
            occurredAt = "2024-01-01T00:00:00Z",
            payload = mapOf("content" to "hello")
        )
        transport.post("/api/v1/events", request, IngestEventResponse::class.java)

        wireMock.verify(
            postRequestedFor(urlEqualTo("/api/v1/events"))
                .withRequestBody(matchingJsonPath("$.sessionId", equalTo("sess-1")))
                .withRequestBody(matchingJsonPath("$.eventType", equalTo("USER_INPUT")))
                .withRequestBody(matchingJsonPath("$.payload.content", equalTo("hello")))
        )
    }
}
