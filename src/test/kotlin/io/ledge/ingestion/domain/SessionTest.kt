package io.ledge.ingestion.domain

import io.ledge.TestFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests the Session aggregate root — the primary ingestion entry point.
 * Session enforces: events can only be ingested while ACTIVE,
 * and terminal transitions (complete/abandon) are one-way.
 */
class SessionTest {

    private fun activeSession() = Session(
        id = TestFixtures.sessionId(),
        agentId = TestFixtures.agentId(),
        tenantId = TestFixtures.tenantId()
    )

    @Test
    fun `ingest on ACTIVE session returns MemoryEvent with correct fields`() {
        val session = activeSession()
        val eventId = TestFixtures.eventId()
        val contextHash = TestFixtures.contextHash()

        val event = session.ingest(
            eventId = eventId,
            eventType = EventType.USER_MESSAGE,
            payload = """{"text": "hello"}""",
            contextHash = contextHash
        )

        // Session-level fields (sessionId, agentId, tenantId) are propagated to the event
        assertEquals(eventId, event.id)
        assertEquals(session.id, event.sessionId)
        assertEquals(session.agentId, event.agentId)
        assertEquals(session.tenantId, event.tenantId)
        assertEquals(EventType.USER_MESSAGE, event.eventType)
        assertEquals("""{"text": "hello"}""", event.payload)
        assertEquals(contextHash, event.contextHash)
        assertNull(event.parentEventId)
    }

    @Test
    fun `ingest assigns monotonically increasing sequence numbers`() {
        val session = activeSession()

        val e1 = session.ingest(eventId = TestFixtures.eventId(), eventType = EventType.USER_MESSAGE, payload = "1")
        val e2 = session.ingest(eventId = TestFixtures.eventId(), eventType = EventType.ASSISTANT_MESSAGE, payload = "2")
        val e3 = session.ingest(eventId = TestFixtures.eventId(), eventType = EventType.TOOL_CALL, payload = "3")

        assertEquals(1L, e1.sequenceNumber)
        assertEquals(2L, e2.sequenceNumber)
        assertEquals(3L, e3.sequenceNumber)
    }

    @Test
    fun `ingest on COMPLETED session throws IllegalStateException`() {
        val session = activeSession()
        session.complete()

        assertThrows<IllegalStateException> {
            session.ingest(eventId = TestFixtures.eventId(), eventType = EventType.USER_MESSAGE, payload = "x")
        }
    }

    @Test
    fun `ingest on ABANDONED session throws IllegalStateException`() {
        val session = activeSession()
        session.abandon()

        assertThrows<IllegalStateException> {
            session.ingest(eventId = TestFixtures.eventId(), eventType = EventType.USER_MESSAGE, payload = "x")
        }
    }

    @Test
    fun `complete from ACTIVE transitions to COMPLETED and sets endedAt`() {
        val session = activeSession()
        assertNull(session.endedAt)

        session.complete()

        assertEquals(SessionStatus.COMPLETED, session.status)
        assertNotNull(session.endedAt)
    }

    @Test
    fun `complete from COMPLETED throws IllegalStateException`() {
        val session = activeSession()
        session.complete()
        assertThrows<IllegalStateException> { session.complete() }
    }

    @Test
    fun `abandon from ACTIVE transitions to ABANDONED and sets endedAt`() {
        val session = activeSession()
        assertNull(session.endedAt)

        session.abandon()

        assertEquals(SessionStatus.ABANDONED, session.status)
        assertNotNull(session.endedAt)
    }

    @Test
    fun `abandon from ABANDONED throws IllegalStateException`() {
        val session = activeSession()
        session.abandon()
        assertThrows<IllegalStateException> { session.abandon() }
    }

    @Test
    fun `events list accumulates ingested events`() {
        val session = activeSession()
        assertTrue(session.events.isEmpty())

        session.ingest(eventId = TestFixtures.eventId(), eventType = EventType.USER_MESSAGE, payload = "a")
        session.ingest(eventId = TestFixtures.eventId(), eventType = EventType.ASSISTANT_MESSAGE, payload = "b")

        assertEquals(2, session.events.size)
        assertEquals("a", session.events[0].payload)
        assertEquals("b", session.events[1].payload)
    }
}
