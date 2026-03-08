package io.ledge.ingestion.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ledge.TestFixtures
import io.ledge.infrastructure.api.GlobalExceptionHandler
import io.ledge.ingestion.application.CreateSessionCommand
import io.ledge.ingestion.application.FakeDomainEventPublisher
import io.ledge.ingestion.application.FakeMemoryEventPublisher
import io.ledge.ingestion.application.FakeMemoryEventQuery
import io.ledge.ingestion.application.FakeSessionRepository
import io.ledge.ingestion.application.IngestionService
import io.ledge.ingestion.domain.EventType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class EventControllerTest {

    private lateinit var sessionRepo: FakeSessionRepository
    private lateinit var service: IngestionService
    private lateinit var client: WebTestClient
    private val objectMapper = ObjectMapper().registerKotlinModule()

    @BeforeEach
    fun setUp() {
        sessionRepo = FakeSessionRepository()
        service = IngestionService(
            sessionRepo,
            FakeMemoryEventPublisher(),
            FakeDomainEventPublisher(),
            FakeMemoryEventQuery()
        )
        val controller = EventController(service, objectMapper)
        client = WebTestClient.bindToController(controller)
            .controllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // --- POST /api/v1/events ---

    @Test
    fun `ingestEvent returns 202 with eventId`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()
        val session = service.createSession(tenantId, CreateSessionCommand(agentId))
        val now = Instant.now().toString()

        client.post().uri("/api/v1/events")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "sessionId": "${session.id.value}",
                    "agentId": "${agentId.value}",
                    "eventType": "USER_INPUT",
                    "occurredAt": "$now",
                    "payload": ${TestFixtures.validPayloadJson(EventType.USER_INPUT)}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("$.eventId").isNotEmpty
    }

    @Test
    fun `ingestEvent returns 400 for invalid payload`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()
        val session = service.createSession(tenantId, CreateSessionCommand(agentId))
        val now = Instant.now().toString()

        client.post().uri("/api/v1/events")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "sessionId": "${session.id.value}",
                    "agentId": "${agentId.value}",
                    "eventType": "USER_INPUT",
                    "occurredAt": "$now",
                    "payload": {"wrong": "fields"}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Bad Request")
    }

    @Test
    fun `ingestEvent returns 404 when session not found`() {
        val tenantId = TestFixtures.tenantId()
        val now = Instant.now().toString()

        client.post().uri("/api/v1/events")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "sessionId": "${TestFixtures.sessionId().value}",
                    "agentId": "${TestFixtures.agentId().value}",
                    "eventType": "USER_INPUT",
                    "occurredAt": "$now",
                    "payload": ${TestFixtures.validPayloadJson(EventType.USER_INPUT)}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.error").isEqualTo("Not Found")
    }

    @Test
    fun `ingestEvent returns 409 when session is completed`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()
        val session = service.createSession(tenantId, CreateSessionCommand(agentId))
        session.complete() // Simulate consumer-side completion
        val now = Instant.now().toString()

        client.post().uri("/api/v1/events")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "sessionId": "${session.id.value}",
                    "agentId": "${agentId.value}",
                    "eventType": "USER_INPUT",
                    "occurredAt": "$now",
                    "payload": ${TestFixtures.validPayloadJson(EventType.USER_INPUT)}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Conflict")
    }

    // --- POST /api/v1/events/batch ---

    @Test
    fun `ingestBatch returns 202 with accepted count`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()
        val session = service.createSession(tenantId, CreateSessionCommand(agentId))
        val now = Instant.now().toString()

        client.post().uri("/api/v1/events/batch")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "events": [
                        {
                            "sessionId": "${session.id.value}",
                            "agentId": "${agentId.value}",
                            "eventType": "USER_INPUT",
                            "occurredAt": "$now",
                            "payload": ${TestFixtures.validPayloadJson(EventType.USER_INPUT)}
                        },
                        {
                            "sessionId": "${session.id.value}",
                            "agentId": "${agentId.value}",
                            "eventType": "AGENT_OUTPUT",
                            "occurredAt": "$now",
                            "payload": ${TestFixtures.validPayloadJson(EventType.AGENT_OUTPUT)}
                        }
                    ]
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isAccepted
            .expectBody()
            .jsonPath("$.accepted").isEqualTo(2)
            .jsonPath("$.results.length()").isEqualTo(2)
            .jsonPath("$.results[0].eventId").isNotEmpty
            .jsonPath("$.results[1].eventId").isNotEmpty
    }

    @Test
    fun `ingestBatch returns 400 for empty batch`() {
        val tenantId = TestFixtures.tenantId()

        client.post().uri("/api/v1/events/batch")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"events": []}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Bad Request")
    }

    @Test
    fun `ingestBatch returns 400 when batch exceeds 500`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()
        val session = service.createSession(tenantId, CreateSessionCommand(agentId))
        val now = Instant.now().toString()

        val events = (1..501).joinToString(",") { i ->
            """
                {
                    "sessionId": "${session.id.value}",
                    "agentId": "${agentId.value}",
                    "eventType": "USER_INPUT",
                    "occurredAt": "$now",
                    "payload": ${TestFixtures.validPayloadJson(EventType.USER_INPUT)}
                }
            """.trimIndent()
        }

        client.post().uri("/api/v1/events/batch")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"events": [$events]}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Bad Request")
    }

    @Test
    fun `ingestBatch returns 400 when any event has invalid payload`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()
        val session = service.createSession(tenantId, CreateSessionCommand(agentId))
        val now = Instant.now().toString()

        client.post().uri("/api/v1/events/batch")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "events": [
                        {
                            "sessionId": "${session.id.value}",
                            "agentId": "${agentId.value}",
                            "eventType": "USER_INPUT",
                            "occurredAt": "$now",
                            "payload": ${TestFixtures.validPayloadJson(EventType.USER_INPUT)}
                        },
                        {
                            "sessionId": "${session.id.value}",
                            "agentId": "${agentId.value}",
                            "eventType": "TOOL_INVOKED",
                            "occurredAt": "$now",
                            "payload": {"missing": "required fields"}
                        }
                    ]
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Bad Request")
    }
}
