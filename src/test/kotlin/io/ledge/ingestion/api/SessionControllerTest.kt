package io.ledge.ingestion.api

import io.ledge.TestFixtures
import io.ledge.infrastructure.api.GlobalExceptionHandler
import io.ledge.ingestion.application.CreateSessionCommand
import io.ledge.ingestion.application.FakeDomainEventPublisher
import io.ledge.ingestion.application.FakeMemoryEventPublisher
import io.ledge.ingestion.application.FakeMemoryEventQuery
import io.ledge.ingestion.application.FakeSessionRepository
import io.ledge.ingestion.application.IngestionService
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
            memoryEventQuery
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
    fun `updateSession to COMPLETED returns 200`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        client.patch().uri("/api/v1/sessions/${session.id.value}")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status": "COMPLETED"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("COMPLETED")
            .jsonPath("$.endedAt").isNotEmpty
    }

    @Test
    fun `updateSession to ABANDONED returns 200`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        client.patch().uri("/api/v1/sessions/${session.id.value}")
            .header("X-Tenant-Id", tenantId.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"status": "ABANDONED"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("ABANDONED")
            .jsonPath("$.endedAt").isNotEmpty
    }

    @Test
    fun `updateSession returns 409 when already completed`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        service.completeSession(session.id, tenantId)

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
            .jsonPath("$[0].sequenceNumber").isEqualTo(1)
            .jsonPath("$[1].sequenceNumber").isEqualTo(2)
    }

    @Test
    fun `getSessionEvents supports pagination with after and limit`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        repeat(3) {
            service.ingestEvent(tenantId, TestFixtures.ingestEventCommand(sessionId = session.id, payload = "e${it + 1}"))
        }
        memoryEventPublisher.publishedEvents.forEach { memoryEventQuery.addEvent(it) }

        client.get().uri("/api/v1/sessions/${session.id.value}/events?after=1&limit=1")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].sequenceNumber").isEqualTo(2)
    }
}
