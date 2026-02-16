package io.ledge.memory.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ledge.TestFixtures
import io.ledge.infrastructure.api.GlobalExceptionHandler
import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.memory.application.FakeDomainEventPublisher
import io.ledge.memory.application.FakeMemoryEntryRepository
import io.ledge.memory.application.FakeMemorySnapshotRepository
import io.ledge.memory.application.FakeObservationEventQuery
import io.ledge.memory.application.MemoryService
import io.ledge.memory.domain.ContentBlock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class ObservationControllerTest {

    private lateinit var observationQuery: FakeObservationEventQuery
    private lateinit var service: MemoryService
    private lateinit var client: WebTestClient

    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        observationQuery = FakeObservationEventQuery()
        service = MemoryService(
            FakeMemorySnapshotRepository(),
            FakeMemoryEntryRepository(),
            FakeDomainEventPublisher(),
            observationQuery
        )
        val controller = ObservationController(service, objectMapper)
        client = WebTestClient.bindToController(controller)
            .controllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // --- GET /api/v1/agents/{agentId}/context ---

    @Test
    fun `getContext returns 200 with context event`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val eventId = TestFixtures.eventId()
        val t1 = Instant.parse("2025-01-01T00:00:00Z")
        val queryAt = Instant.parse("2025-01-01T01:00:00Z")

        val event = contextAssembledEvent(
            eventId = eventId,
            agentId = agentId,
            tenantId = tenantId,
            occurredAt = t1,
            payload = """{"blocks": []}""",
            contextHash = ContextHash(TestFixtures.VALID_SHA256)
        )
        observationQuery.addEvent(event)

        client.get().uri("/api/v1/agents/${agentId.value}/context?at=$queryAt")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.eventId").isEqualTo(eventId.value.toString())
            .jsonPath("$.agentId").isEqualTo(agentId.value.toString())
            .jsonPath("$.occurredAt").isEqualTo(t1.toString())
            .jsonPath("$.payload.blocks").isArray
            .jsonPath("$.contextHash").isEqualTo(TestFixtures.VALID_SHA256)
    }

    @Test
    fun `getContext returns 404 when no event found`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val queryAt = Instant.parse("2025-01-01T01:00:00Z")

        client.get().uri("/api/v1/agents/${agentId.value}/context?at=$queryAt")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isNotFound
    }

    // --- GET /api/v1/agents/{agentId}/context/diff ---

    @Test
    fun `getContextDiff returns 200 with diff`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val t1 = Instant.parse("2025-01-01T00:00:00Z")
        val t2 = Instant.parse("2025-01-01T01:00:00Z")
        val queryFrom = "2025-01-01T00:30:00Z"
        val queryTo = "2025-01-01T01:30:00Z"

        val fromBlocks = listOf(
            ContentBlock("system", "You are helpful", 3, null),
            ContentBlock("user", "Hello", 1, null)
        )
        val toBlocks = listOf(
            ContentBlock("system", "You are very helpful", 4, null),
            ContentBlock("document", "New document", 2, "rag")
        )

        val fromEvent = contextAssembledEvent(
            agentId = agentId,
            tenantId = tenantId,
            occurredAt = t1,
            payload = objectMapper.writeValueAsString(fromBlocks)
        )
        val toEvent = contextAssembledEvent(
            agentId = agentId,
            tenantId = tenantId,
            occurredAt = t2,
            payload = objectMapper.writeValueAsString(toBlocks)
        )
        observationQuery.addEvent(fromEvent)
        observationQuery.addEvent(toEvent)

        client.get().uri("/api/v1/agents/${agentId.value}/context/diff?from=$queryFrom&to=$queryTo")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fromEventId").isEqualTo(fromEvent.id.value.toString())
            .jsonPath("$.toEventId").isEqualTo(toEvent.id.value.toString())
            .jsonPath("$.from").isEqualTo(queryFrom)
            .jsonPath("$.to").isEqualTo(queryTo)
            .jsonPath("$.addedBlocks.length()").isEqualTo(1)
            .jsonPath("$.addedBlocks[0].blockType").isEqualTo("document")
            .jsonPath("$.removedBlocks.length()").isEqualTo(1)
            .jsonPath("$.removedBlocks[0].blockType").isEqualTo("user")
            .jsonPath("$.modifiedBlocks.length()").isEqualTo(1)
            .jsonPath("$.modifiedBlocks[0].blockType").isEqualTo("system")
            .jsonPath("$.modifiedBlocks[0].before.content").isEqualTo("You are helpful")
            .jsonPath("$.modifiedBlocks[0].after.content").isEqualTo("You are very helpful")
    }

    @Test
    fun `getContextDiff returns 404 when no from-event found`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()

        client.get().uri("/api/v1/agents/${agentId.value}/context/diff?from=2025-01-01T00:00:00Z&to=2025-01-01T01:00:00Z")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isNotFound
    }

    // --- GET /api/v1/sessions/{sessionId}/audit ---

    @Test
    fun `getAuditTrail returns 200 with events in sequence order`() {
        val sessionId = TestFixtures.sessionId()
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()

        val e1 = memoryEvent(sessionId, agentId, tenantId, EventType.USER_INPUT, 1L)
        val e2 = memoryEvent(sessionId, agentId, tenantId, EventType.AGENT_OUTPUT, 2L)
        observationQuery.addEvent(e2)
        observationQuery.addEvent(e1)

        client.get().uri("/api/v1/sessions/${sessionId.value}/audit")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].sequenceNumber").isEqualTo(1)
            .jsonPath("$[0].eventType").isEqualTo("USER_INPUT")
            .jsonPath("$[0].sessionId").isEqualTo(sessionId.value.toString())
            .jsonPath("$[0].payload").isNotEmpty
            .jsonPath("$[1].sequenceNumber").isEqualTo(2)
            .jsonPath("$[1].eventType").isEqualTo("AGENT_OUTPUT")
    }

    @Test
    fun `getAuditTrail returns 200 with empty list when no events`() {
        val sessionId = TestFixtures.sessionId()
        val tenantId = TestFixtures.tenantId()

        client.get().uri("/api/v1/sessions/${sessionId.value}/audit")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(0)
    }

    // --- Test helpers ---

    private fun contextAssembledEvent(
        eventId: io.ledge.shared.EventId = TestFixtures.eventId(),
        agentId: io.ledge.shared.AgentId = TestFixtures.agentId(),
        tenantId: io.ledge.shared.TenantId = TestFixtures.tenantId(),
        occurredAt: Instant = Instant.now(),
        payload: String = "[]",
        contextHash: ContextHash? = null
    ): MemoryEvent = MemoryEvent(
        id = eventId,
        sessionId = TestFixtures.sessionId(),
        agentId = agentId,
        tenantId = tenantId,
        eventType = EventType.CONTEXT_ASSEMBLED,
        sequenceNumber = 1L,
        occurredAt = occurredAt,
        payload = payload,
        contextHash = contextHash,
        parentEventId = null,
        schemaVersion = SchemaVersion(1)
    )

    private fun memoryEvent(
        sessionId: io.ledge.shared.SessionId,
        agentId: io.ledge.shared.AgentId,
        tenantId: io.ledge.shared.TenantId,
        eventType: EventType,
        sequenceNumber: Long
    ): MemoryEvent = MemoryEvent(
        id = TestFixtures.eventId(),
        sessionId = sessionId,
        agentId = agentId,
        tenantId = tenantId,
        eventType = eventType,
        sequenceNumber = sequenceNumber,
        occurredAt = Instant.now(),
        payload = """{"data": "test"}""",
        contextHash = null,
        parentEventId = null,
        schemaVersion = SchemaVersion(1)
    )
}
