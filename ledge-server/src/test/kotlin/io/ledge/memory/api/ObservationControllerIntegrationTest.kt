package io.ledge.memory.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ledge.TestFixtures
import io.ledge.infrastructure.TestContainers
import io.ledge.infrastructure.api.GlobalExceptionHandler
import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.ingestion.infrastructure.ClickHouseMemoryEventWriter
import io.ledge.memory.application.FakeDomainEventPublisher
import io.ledge.memory.application.FakeMemoryEntryRepository
import io.ledge.memory.application.FakeMemorySnapshotRepository
import io.ledge.memory.application.MemoryService
import io.ledge.memory.domain.ContentBlock
import io.ledge.memory.infrastructure.ClickHouseObservationEventQuery
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.ledge.shared.AgentId
import io.ledge.shared.EventId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import java.sql.DriverManager
import java.time.Instant
import java.time.temporal.ChronoUnit

@Tag("integration")
class ObservationControllerIntegrationTest {

    private val url = TestContainers.clickHouseUrl()
    private val writer = ClickHouseMemoryEventWriter(url, SimpleMeterRegistry())
    private val observationQuery = ClickHouseObservationEventQuery(url)
    private val objectMapper = jacksonObjectMapper()

    private lateinit var client: WebTestClient

    @BeforeEach
    fun setUp() {
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { it.execute("TRUNCATE TABLE ledge.memory_events") }
        }

        val service = MemoryService(
            FakeMemorySnapshotRepository(),
            FakeMemoryEntryRepository(),
            FakeDomainEventPublisher(),
            observationQuery,
            SimpleMeterRegistry()
        )
        val controller = ObservationController(service, objectMapper)
        client = WebTestClient.bindToController(controller)
            .controllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `context query returns event with payload as JSON object`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val eventId = TestFixtures.eventId()
        val t1 = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val queryAt = t1.plusSeconds(10)

        val payload = objectMapper.writeValueAsString(
            listOf(ContentBlock("system", "You are helpful", 3, null))
        )
        val event = makeEvent(
            eventId = eventId,
            agentId = agentId,
            tenantId = tenantId,
            eventType = EventType.CONTEXT_ASSEMBLED,
            occurredAt = t1,
            payload = payload,
            contextHash = ContextHash(TestFixtures.VALID_SHA256)
        )
        writer.write(event)

        client.get().uri("/api/v1/agents/${agentId.value}/context?at=$queryAt")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.eventId").isEqualTo(eventId.value.toString())
            .jsonPath("$.payload").isArray
            .jsonPath("$.payload[0].blockType").isEqualTo("system")
            .jsonPath("$.payload[0].content").isEqualTo("You are helpful")
            .jsonPath("$.payload[0].tokenCount").isEqualTo(3)
            .jsonPath("$.occurredAt").isNotEmpty
            .jsonPath("$.contextHash").isEqualTo(TestFixtures.VALID_SHA256)
    }

    @Test
    fun `context diff computes added, removed, modified blocks through ClickHouse`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val sessionId = TestFixtures.sessionId()
        val t1 = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val t2 = t1.plusSeconds(5)
        val queryFrom = t1.plusSeconds(2)
        val queryTo = t2.plusSeconds(2)

        val fromBlocks = listOf(
            ContentBlock("system", "You are helpful", 3, null),
            ContentBlock("user", "Hello", 1, null)
        )
        val toBlocks = listOf(
            ContentBlock("system", "You are very helpful", 4, null),
            ContentBlock("document", "New document", 2, "rag")
        )

        val fromEvent = makeEvent(
            agentId = agentId,
            tenantId = tenantId,
            sessionId = sessionId,
            eventType = EventType.CONTEXT_ASSEMBLED,
            occurredAt = t1,
            payload = objectMapper.writeValueAsString(fromBlocks)
        )
        val toEvent = makeEvent(
            agentId = agentId,
            tenantId = tenantId,
            sessionId = sessionId,
            eventType = EventType.CONTEXT_ASSEMBLED,
            occurredAt = t2,
            payload = objectMapper.writeValueAsString(toBlocks)
        )
        writer.writeAll(listOf(fromEvent, toEvent))

        client.get().uri("/api/v1/agents/${agentId.value}/context/diff?from=$queryFrom&to=$queryTo")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fromEventId").isEqualTo(fromEvent.id.value.toString())
            .jsonPath("$.toEventId").isEqualTo(toEvent.id.value.toString())
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
    fun `audit trail returns ordered events with all fields`() {
        val sessionId = TestFixtures.sessionId()
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val t1 = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val e1 = makeEvent(
            sessionId = sessionId, agentId = agentId, tenantId = tenantId,
            eventType = EventType.USER_INPUT,
            occurredAt = t1, payload = """{"text":"hello"}"""
        )
        val e2 = makeEvent(
            sessionId = sessionId, agentId = agentId, tenantId = tenantId,
            eventType = EventType.INFERENCE_REQUESTED,
            occurredAt = t1.plusSeconds(1), payload = """{"model":"gpt-4"}"""
        )
        val e3 = makeEvent(
            sessionId = sessionId, agentId = agentId, tenantId = tenantId,
            eventType = EventType.AGENT_OUTPUT,
            occurredAt = t1.plusSeconds(2), payload = """{"text":"Hi there!"}"""
        )
        writer.writeAll(listOf(e1, e2, e3))

        client.get().uri("/api/v1/sessions/${sessionId.value}/audit")
            .header("X-Tenant-Id", tenantId.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(3)
            .jsonPath("$[0].eventType").isEqualTo("USER_INPUT")
            .jsonPath("$[0].payload").isMap
            .jsonPath("$[0].payload.text").isEqualTo("hello")
            .jsonPath("$[1].eventType").isEqualTo("INFERENCE_REQUESTED")
            .jsonPath("$[2].eventType").isEqualTo("AGENT_OUTPUT")
            .jsonPath("$[2].sessionId").isEqualTo(sessionId.value.toString())
            .jsonPath("$[2].agentId").isEqualTo(agentId.value.toString())
            .jsonPath("$[2].tenantId").isEqualTo(tenantId.value.toString())
    }

    private fun makeEvent(
        eventId: EventId = TestFixtures.eventId(),
        sessionId: SessionId = TestFixtures.sessionId(),
        agentId: AgentId = TestFixtures.agentId(),
        tenantId: TenantId = TestFixtures.tenantId(),
        eventType: EventType = EventType.USER_INPUT,
        occurredAt: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        payload: String = """{"text":"hello"}""",
        contextHash: ContextHash? = null
    ): MemoryEvent = MemoryEvent(
        id = eventId,
        sessionId = sessionId,
        agentId = agentId,
        tenantId = tenantId,
        eventType = eventType,
        occurredAt = occurredAt,
        payload = payload,
        contextHash = contextHash,
        parentEventId = null,
        schemaVersion = SchemaVersion(1)
    )
}
