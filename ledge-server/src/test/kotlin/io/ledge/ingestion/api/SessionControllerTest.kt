package io.ledge.ingestion.api

import io.ledge.TestFixtures
import io.ledge.infrastructure.api.GlobalExceptionHandler
import io.ledge.ingestion.application.CreateSessionCommand
import io.ledge.ingestion.application.FakeDomainEventPublisher
import io.ledge.ingestion.application.FakeMemoryEventPublisher
import io.ledge.ingestion.application.FakeMemoryEventQuery
import io.ledge.ingestion.application.FakeSessionRepository
import io.ledge.ingestion.application.IngestionService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class SessionControllerTest {

    private lateinit var sessionRepo: FakeSessionRepository
    private lateinit var memoryEventPublisher: FakeMemoryEventPublisher
    private lateinit var memoryEventQuery: FakeMemoryEventQuery
    private lateinit var service: IngestionService
    private lateinit var client: WebTestClient

    @BeforeEach
    fun setUp() {
        sessionRepo = FakeSessionRepository()
        memoryEventPublisher = FakeMemoryEventPublisher()
        memoryEventQuery = FakeMemoryEventQuery()
        service = IngestionService(
            sessionRepo,
            memoryEventPublisher,
            FakeDomainEventPublisher(),
            memoryEventQuery,
            SimpleMeterRegistry()
        )
        val controller = SessionController(service)
        client = WebTestClient.bindToController(controller)
            .controllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // --- POST /api/v1/sessions ---

    @Test
    fun `createSession returns 201 with session details`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()

        client.post().uri("/api/v1/sessions")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"agentId": "${agentId.value}"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.sessionId").isNotEmpty
            .jsonPath("$.agentId").isEqualTo(agentId.value.toString())
            .jsonPath("$.tenantId").isEqualTo(tenantId.value.toString())
            .jsonPath("$.status").isEqualTo("ACTIVE")
    }

    // --- GET /api/v1/sessions/{sessionId} ---

    @Test
    fun `getSession returns 200 when session exists`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()
        val session = service.createSession(tenantId, CreateSessionCommand(agentId))

        client.get().uri("/api/v1/sessions/${session.id.value}")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.sessionId").isEqualTo(session.id.value.toString())
            .jsonPath("$.status").isEqualTo("ACTIVE")
    }

    @Test
    fun `getSession returns 404 when session not found`() {
        val tenantId = TestFixtures.tenantId()

        client.get().uri("/api/v1/sessions/${TestFixtures.sessionId().value}")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isNotFound
    }

    // --- PATCH /api/v1/sessions/{sessionId} ---

    @Test
    fun `updateSession to COMPLETED returns 202`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        client.patch().uri("/api/v1/sessions/${session.id.value}")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status": "COMPLETED"}""")
            .exchange()
            .expectStatus().isAccepted
    }

    @Test
    fun `updateSession to ABANDONED returns 202`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        client.patch().uri("/api/v1/sessions/${session.id.value}")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status": "ABANDONED"}""")
            .exchange()
            .expectStatus().isAccepted
    }

    @Test
    fun `updateSession returns 409 when already completed`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        session.complete() // Simulate consumer-side completion

        client.patch().uri("/api/v1/sessions/${session.id.value}")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status": "COMPLETED"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Conflict")
    }

    // --- GET /api/v1/sessions/{sessionId}/events ---

    @Test
    fun `getSessionEvents returns 200 with ordered events`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()
        val session = service.createSession(tenantId, CreateSessionCommand(agentId))

        // Ingest events via service so fake publisher captures them
        service.ingestEvent(tenantId, TestFixtures.ingestEventCommand(sessionId = session.id, payload = "first"))
        service.ingestEvent(tenantId, TestFixtures.ingestEventCommand(sessionId = session.id, payload = "second"))

        // Seed read-side fake
        memoryEventPublisher.publishedEvents.forEach { memoryEventQuery.addEvent(it) }

        client.get().uri("/api/v1/sessions/${session.id.value}/events")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].payload").isEqualTo("first")
            .jsonPath("$[1].payload").isEqualTo("second")
    }

    @Test
    fun `getSessionEvents supports pagination with after timestamp and limit`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        repeat(3) {
            service.ingestEvent(tenantId, TestFixtures.ingestEventCommand(sessionId = session.id, payload = "e${it + 1}"))
        }
        memoryEventPublisher.publishedEvents.forEach { memoryEventQuery.addEvent(it) }

        val firstEventTime = memoryEventPublisher.publishedEvents[0].occurredAt

        client.get().uri("/api/v1/sessions/${session.id.value}/events?after=$firstEventTime&limit=1")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
    }
}
