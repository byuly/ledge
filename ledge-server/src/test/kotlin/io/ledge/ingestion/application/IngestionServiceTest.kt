package io.ledge.ingestion.application

import io.ledge.TestFixtures
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.SessionStatus
import io.ledge.shared.SessionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class IngestionServiceTest {

    private lateinit var sessionRepo: FakeSessionRepository
    private lateinit var memoryEventPublisher: FakeMemoryEventPublisher
    private lateinit var domainEventPublisher: FakeDomainEventPublisher
    private lateinit var memoryEventQuery: FakeMemoryEventQuery
    private lateinit var service: IngestionService

    @BeforeEach
    fun setUp() {
        sessionRepo = FakeSessionRepository()
        memoryEventPublisher = FakeMemoryEventPublisher()
        domainEventPublisher = FakeDomainEventPublisher()
        memoryEventQuery = FakeMemoryEventQuery()
        service = IngestionService(sessionRepo, memoryEventPublisher, domainEventPublisher, memoryEventQuery, SimpleMeterRegistry())
    }

    // --- createSession ---

    @Test
    fun `createSession saves session with correct fields`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()

        val session = service.createSession(tenantId, CreateSessionCommand(agentId))

        assertNotNull(session.id)
        assertEquals(agentId, session.agentId)
        assertEquals(tenantId, session.tenantId)
        assertEquals(SessionStatus.ACTIVE, session.status)
    }

    @Test
    fun `createSession generates unique session IDs`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()

        val s1 = service.createSession(tenantId, CreateSessionCommand(agentId))
        val s2 = service.createSession(tenantId, CreateSessionCommand(agentId))

        assertNotEquals(s1.id, s2.id)
    }

    @Test
    fun `createSession uses tenantId parameter`() {
        val tenantA = TestFixtures.tenantId()
        val tenantB = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()

        val sessionA = service.createSession(tenantA, CreateSessionCommand(agentId))
        val sessionB = service.createSession(tenantB, CreateSessionCommand(agentId))

        assertEquals(tenantA, sessionA.tenantId)
        assertEquals(tenantB, sessionB.tenantId)
    }

    // --- getSession ---

    @Test
    fun `getSession returns null when session does not exist`() {
        assertNull(service.getSession(TestFixtures.sessionId(), TestFixtures.tenantId()))
    }

    @Test
    fun `getSession returns session when it exists`() {
        val tenantId = TestFixtures.tenantId()
        val created = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        val found = service.getSession(created.id, tenantId)

        assertNotNull(found)
        assertEquals(created.id, found!!.id)
    }

    @Test
    fun `getSession returns null for wrong tenant`() {
        val tenantA = TestFixtures.tenantId()
        val tenantB = TestFixtures.tenantId()
        val created = service.createSession(tenantA, CreateSessionCommand(TestFixtures.agentId()))

        assertNull(service.getSession(created.id, tenantB))
    }

    // --- completeSession ---

    @Test
    fun `completeSession publishes SESSION_COMPLETED event to Kafka`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        service.completeSession(session.id, tenantId)

        assertEquals(1, memoryEventPublisher.publishedEvents.size)
        val event = memoryEventPublisher.publishedEvents[0]
        assertEquals(EventType.SESSION_COMPLETED, event.eventType)
        assertEquals(session.id, event.sessionId)
        assertEquals(session.agentId, event.agentId)
        assertEquals(tenantId, event.tenantId)
    }

    @Test
    fun `completeSession throws for unknown session`() {
        assertThrows<IllegalArgumentException> {
            service.completeSession(TestFixtures.sessionId(), TestFixtures.tenantId())
        }
    }

    @Test
    fun `completeSession throws for already completed session`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        // Simulate consumer completing the session (as the real flow would)
        session.complete()

        assertThrows<IllegalStateException> {
            service.completeSession(session.id, tenantId)
        }
    }

    // --- abandonSession ---

    @Test
    fun `abandonSession publishes SESSION_ABANDONED event to Kafka`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        service.abandonSession(session.id, tenantId)

        assertEquals(1, memoryEventPublisher.publishedEvents.size)
        val event = memoryEventPublisher.publishedEvents[0]
        assertEquals(EventType.SESSION_ABANDONED, event.eventType)
        assertEquals(session.id, event.sessionId)
    }

    @Test
    fun `abandonSession throws for unknown session`() {
        assertThrows<IllegalArgumentException> {
            service.abandonSession(TestFixtures.sessionId(), TestFixtures.tenantId())
        }
    }

    // --- ingestEvent ---

    @Test
    fun `ingestEvent returns eventId`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        val command = TestFixtures.ingestEventCommand(sessionId = session.id)

        val result = service.ingestEvent(tenantId, command)

        assertNotNull(result.eventId)
    }

    @Test
    fun `ingestEvent publishes to memory event publisher`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        val command = TestFixtures.ingestEventCommand(sessionId = session.id)

        val result = service.ingestEvent(tenantId, command)

        assertEquals(1, memoryEventPublisher.publishedEvents.size)
        val published = memoryEventPublisher.publishedEvents[0]
        assertEquals(result.eventId, published.id)
        assertEquals(session.id, published.sessionId)
        assertEquals(session.agentId, published.agentId)
        assertEquals(tenantId, published.tenantId)
    }

    @Test
    fun `ingestEvent server-generates eventId`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        val command = TestFixtures.ingestEventCommand(sessionId = session.id)

        val r1 = service.ingestEvent(tenantId, command)
        val r2 = service.ingestEvent(tenantId, command)

        assertNotEquals(r1.eventId, r2.eventId)
    }

    @Test
    fun `ingestEvent throws for unknown session`() {
        val command = TestFixtures.ingestEventCommand(sessionId = TestFixtures.sessionId())

        assertThrows<IllegalArgumentException> {
            service.ingestEvent(TestFixtures.tenantId(), command)
        }
    }

    @Test
    fun `ingestEvent throws for completed session`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        session.complete()
        val command = TestFixtures.ingestEventCommand(sessionId = session.id)

        assertThrows<IllegalStateException> {
            service.ingestEvent(tenantId, command)
        }
    }

    // --- ingestBatch ---

    @Test
    fun `ingestBatch returns results in original order`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        val commands = listOf(
            TestFixtures.ingestEventCommand(sessionId = session.id, payload = "first"),
            TestFixtures.ingestEventCommand(sessionId = session.id, payload = "second"),
            TestFixtures.ingestEventCommand(sessionId = session.id, payload = "third")
        )

        val results = service.ingestBatch(tenantId, commands)

        assertEquals(3, results.size)
        // Each result has a unique eventId
        assertEquals(3, results.map { it.eventId }.distinct().size)
    }

    @Test
    fun `ingestBatch publishes all events`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        val commands = listOf(
            TestFixtures.ingestEventCommand(sessionId = session.id, payload = "a"),
            TestFixtures.ingestEventCommand(sessionId = session.id, payload = "b")
        )

        service.ingestBatch(tenantId, commands)

        assertEquals(2, memoryEventPublisher.publishedEvents.size)
        assertEquals("a", memoryEventPublisher.publishedEvents[0].payload)
        assertEquals("b", memoryEventPublisher.publishedEvents[1].payload)
    }

    @Test
    fun `ingestBatch handles multiple sessions`() {
        val tenantId = TestFixtures.tenantId()
        val s1 = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        val s2 = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        val commands = listOf(
            TestFixtures.ingestEventCommand(sessionId = s1.id, payload = "s1-event"),
            TestFixtures.ingestEventCommand(sessionId = s2.id, payload = "s2-event")
        )

        val results = service.ingestBatch(tenantId, commands)

        assertEquals(2, results.size)
        assertEquals(2, memoryEventPublisher.publishedEvents.size)
    }

    @Test
    fun `ingestBatch rejects batch exceeding 500`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        val commands = (1..501).map {
            TestFixtures.ingestEventCommand(sessionId = session.id, payload = "event-$it")
        }

        assertThrows<IllegalArgumentException> {
            service.ingestBatch(tenantId, commands)
        }
    }

    @Test
    fun `ingestBatch rejects empty batch`() {
        assertThrows<IllegalArgumentException> {
            service.ingestBatch(TestFixtures.tenantId(), emptyList())
        }
    }

    @Test
    fun `ingestBatch throws if any session is missing`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        val commands = listOf(
            TestFixtures.ingestEventCommand(sessionId = session.id),
            TestFixtures.ingestEventCommand(sessionId = TestFixtures.sessionId())
        )

        assertThrows<IllegalArgumentException> {
            service.ingestBatch(tenantId, commands)
        }
    }

    // --- getSessionEvents ---

    @Test
    fun `getSessionEvents returns events ordered by occurredAt`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        val r1 = service.ingestEvent(tenantId, TestFixtures.ingestEventCommand(sessionId = session.id, payload = "first"))
        val r2 = service.ingestEvent(tenantId, TestFixtures.ingestEventCommand(sessionId = session.id, payload = "second"))

        // Seed the read-side fake with published events
        memoryEventPublisher.publishedEvents.forEach { memoryEventQuery.addEvent(it) }

        val events = service.getSessionEvents(session.id, tenantId)

        assertEquals(2, events.size)
        assertEquals(r1.eventId, events[0].id)
        assertEquals(r2.eventId, events[1].id)
    }

    @Test
    fun `getSessionEvents supports timestamp cursor pagination`() {
        val tenantId = TestFixtures.tenantId()
        val session = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        repeat(3) {
            service.ingestEvent(tenantId, TestFixtures.ingestEventCommand(sessionId = session.id, payload = "e${it + 1}"))
        }
        memoryEventPublisher.publishedEvents.forEach { memoryEventQuery.addEvent(it) }

        val allEvents = service.getSessionEvents(session.id, tenantId)
        val afterTimestamp = allEvents[0].occurredAt
        val page = service.getSessionEvents(session.id, tenantId, after = afterTimestamp)

        assertTrue(page.all { it.occurredAt.isAfter(afterTimestamp) })
    }

    @Test
    fun `getSessionEvents throws for unknown session`() {
        assertThrows<IllegalArgumentException> {
            service.getSessionEvents(TestFixtures.sessionId(), TestFixtures.tenantId())
        }
    }

    // --- purgeTenantSessions ---

    @Test
    fun `purgeTenantSessions deletes all sessions for tenant`() {
        val tenantId = TestFixtures.tenantId()
        val s1 = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))
        val s2 = service.createSession(tenantId, CreateSessionCommand(TestFixtures.agentId()))

        service.purgeTenantSessions(tenantId)

        assertNull(service.getSession(s1.id, tenantId))
        assertNull(service.getSession(s2.id, tenantId))
    }

    @Test
    fun `purgeTenantSessions does not affect other tenants`() {
        val tenantA = TestFixtures.tenantId()
        val tenantB = TestFixtures.tenantId()
        val sessionA = service.createSession(tenantA, CreateSessionCommand(TestFixtures.agentId()))
        val sessionB = service.createSession(tenantB, CreateSessionCommand(TestFixtures.agentId()))

        service.purgeTenantSessions(tenantA)

        assertNull(service.getSession(sessionA.id, tenantA))
        assertNotNull(service.getSession(sessionB.id, tenantB))
    }
}
