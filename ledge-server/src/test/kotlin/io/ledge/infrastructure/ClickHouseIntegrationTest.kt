package io.ledge.infrastructure

import io.ledge.TestFixtures
import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.ingestion.infrastructure.ClickHouseMemoryEventQuery
import io.ledge.ingestion.infrastructure.ClickHouseMemoryEventWriter
import io.ledge.memory.infrastructure.ClickHouseObservationEventQuery
import io.ledge.shared.*
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.time.Instant
import java.time.temporal.ChronoUnit

@Tag("integration")
class ClickHouseIntegrationTest {

    private val url = TestContainers.clickHouseUrl()
    private val writer = ClickHouseMemoryEventWriter(url, SimpleMeterRegistry())
    private val memoryEventQuery = ClickHouseMemoryEventQuery(url)
    private val observationEventQuery = ClickHouseObservationEventQuery(url)

    @BeforeEach
    fun cleanTable() {
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { it.execute("TRUNCATE TABLE ledge.memory_events") }
        }
    }

    private fun makeEvent(
        sessionId: SessionId = TestFixtures.sessionId(),
        agentId: AgentId = TestFixtures.agentId(),
        tenantId: TenantId = TestFixtures.tenantId(),
        eventType: EventType = EventType.USER_INPUT,
        occurredAt: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        payload: String = """{"text":"hello"}""",
        contextHash: ContextHash? = null,
        parentEventId: EventId? = null,
        schemaVersion: SchemaVersion = SchemaVersion(1)
    ): MemoryEvent = MemoryEvent(
        id = TestFixtures.eventId(),
        sessionId = sessionId,
        agentId = agentId,
        tenantId = tenantId,
        eventType = eventType,
        occurredAt = occurredAt,
        payload = payload,
        contextHash = contextHash,
        parentEventId = parentEventId,
        schemaVersion = schemaVersion
    )

    @Test
    fun `write and query single event round-trip`() {
        val tenantId = TestFixtures.tenantId()
        val sessionId = TestFixtures.sessionId()
        val event = makeEvent(sessionId = sessionId, tenantId = tenantId)

        writer.write(event)

        val results = memoryEventQuery.findBySessionId(sessionId, tenantId)
        assertEquals(1, results.size)
        val found = results[0]
        assertEquals(event.id, found.id)
        assertEquals(event.eventType, found.eventType)
        assertEquals(event.payload, found.payload)
    }

    @Test
    fun `writeAll batch insert`() {
        val tenantId = TestFixtures.tenantId()
        val sessionId = TestFixtures.sessionId()
        val agentId = TestFixtures.agentId()
        val baseTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val events = (1..5).map { i ->
            makeEvent(
                sessionId = sessionId,
                agentId = agentId,
                tenantId = tenantId,
                occurredAt = baseTime.plusMillis(i.toLong())
            )
        }
        writer.writeAll(events)

        val results = memoryEventQuery.findBySessionId(sessionId, tenantId)
        assertEquals(5, results.size)
    }

    @Test
    fun `findBySessionId with cursor pagination`() {
        val tenantId = TestFixtures.tenantId()
        val sessionId = TestFixtures.sessionId()
        val agentId = TestFixtures.agentId()
        val baseTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val events = (1..10).map { i ->
            makeEvent(
                sessionId = sessionId,
                agentId = agentId,
                tenantId = tenantId,
                occurredAt = baseTime.plusMillis(i.toLong())
            )
        }
        writer.writeAll(events)

        val page1 = memoryEventQuery.findBySessionId(sessionId, tenantId, after = null, limit = 3)
        assertEquals(3, page1.size)

        val page2 = memoryEventQuery.findBySessionId(sessionId, tenantId, after = page1.last().occurredAt, limit = 3)
        assertEquals(3, page2.size)
        assertTrue(page2[0].occurredAt.isAfter(page1.last().occurredAt))
    }

    @Test
    fun `tenant isolation - different tenants see different events`() {
        val tenant1 = TestFixtures.tenantId()
        val tenant2 = TestFixtures.tenantId()
        val sessionId = TestFixtures.sessionId()

        writer.write(makeEvent(sessionId = sessionId, tenantId = tenant1))
        writer.write(makeEvent(sessionId = sessionId, tenantId = tenant2))

        assertEquals(1, memoryEventQuery.findBySessionId(sessionId, tenant1).size)
        assertEquals(1, memoryEventQuery.findBySessionId(sessionId, tenant2).size)
    }

    @Test
    fun `context_hash empty string maps to null`() {
        val tenantId = TestFixtures.tenantId()
        val sessionId = TestFixtures.sessionId()

        val eventWithoutHash = makeEvent(sessionId = sessionId, tenantId = tenantId, contextHash = null)
        writer.write(eventWithoutHash)

        val results = memoryEventQuery.findBySessionId(sessionId, tenantId)
        assertNull(results[0].contextHash)
    }

    @Test
    fun `context_hash round-trip with value`() {
        val tenantId = TestFixtures.tenantId()
        val sessionId = TestFixtures.sessionId()
        val hash = TestFixtures.contextHash()

        writer.write(makeEvent(sessionId = sessionId, tenantId = tenantId, contextHash = hash))

        val results = memoryEventQuery.findBySessionId(sessionId, tenantId)
        assertEquals(hash, results[0].contextHash)
    }

    @Test
    fun `parent_event_id nullable round-trip`() {
        val tenantId = TestFixtures.tenantId()
        val sessionId = TestFixtures.sessionId()
        val parentId = TestFixtures.eventId()
        val baseTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        writer.write(makeEvent(sessionId = sessionId, tenantId = tenantId, parentEventId = null, occurredAt = baseTime))
        writer.write(makeEvent(sessionId = sessionId, tenantId = tenantId, parentEventId = parentId, occurredAt = baseTime.plusMillis(1)))

        val results = memoryEventQuery.findBySessionId(sessionId, tenantId)
        assertNull(results[0].parentEventId)
        assertEquals(parentId, results[1].parentEventId)
    }

    @Test
    fun `findLatestContextAssembled returns correct event`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()
        val sessionId = TestFixtures.sessionId()
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val older = makeEvent(
            sessionId = sessionId, agentId = agentId, tenantId = tenantId,
            eventType = EventType.CONTEXT_ASSEMBLED,
            occurredAt = now.minusSeconds(120)
        )
        val newer = makeEvent(
            sessionId = sessionId, agentId = agentId, tenantId = tenantId,
            eventType = EventType.CONTEXT_ASSEMBLED,
            occurredAt = now.minusSeconds(60)
        )
        val nonContext = makeEvent(
            sessionId = sessionId, agentId = agentId, tenantId = tenantId,
            eventType = EventType.USER_INPUT,
            occurredAt = now.minusSeconds(30)
        )
        writer.writeAll(listOf(older, newer, nonContext))

        val result = observationEventQuery.findLatestContextAssembled(agentId, tenantId, now)
        assertNotNull(result)
        assertEquals(newer.id, result!!.id)
        assertEquals(EventType.CONTEXT_ASSEMBLED, result.eventType)
    }

    @Test
    fun `findLatestContextAssembled returns null when no matching events`() {
        val result = observationEventQuery.findLatestContextAssembled(
            TestFixtures.agentId(), TestFixtures.tenantId(), Instant.now()
        )
        assertNull(result)
    }

    @Test
    fun `observation findBySessionId returns all events ordered by occurredAt`() {
        val tenantId = TestFixtures.tenantId()
        val sessionId = TestFixtures.sessionId()
        val agentId = TestFixtures.agentId()
        val baseTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val events = (1..3).map { i ->
            makeEvent(
                sessionId = sessionId,
                agentId = agentId,
                tenantId = tenantId,
                occurredAt = baseTime.plusMillis(i.toLong())
            )
        }
        writer.writeAll(events)

        val results = observationEventQuery.findBySessionId(sessionId, tenantId)
        assertEquals(3, results.size)
        // Verify ordered by occurredAt
        assertTrue(results[0].occurredAt <= results[1].occurredAt)
        assertTrue(results[1].occurredAt <= results[2].occurredAt)
    }
}
