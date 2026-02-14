package io.ledge.shared

import org.junit.jupiter.api.Test
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue


class DomainEventTest {

    @Test
    fun `SessionCompleted carries correct fields and implements DomainEvent`() {
        val sessionId = SessionId.generate()
        val agentId = AgentId.generate()
        val tenantId = TenantId.generate()
        val now = Instant.now()

        val event = DomainEvent.SessionCompleted(
            sessionId = sessionId,
            agentId = agentId,
            tenantId = tenantId,
            occurredAt = now
        )

        assertTrue(event is DomainEvent)
        assertEquals(sessionId, event.sessionId)
        assertEquals(agentId, event.agentId)
        assertEquals(tenantId, event.tenantId)
        assertEquals(now, event.occurredAt)
    }

    @Test
    fun `TenantPurged carries correct fields and implements DomainEvent`() {
        val tenantId = TenantId.generate()
        val now = Instant.now()

        val event = DomainEvent.TenantPurged(
            tenantId = tenantId,
            occurredAt = now
        )

        assertTrue(event is DomainEvent)
        assertEquals(tenantId, event.tenantId)
        assertEquals(now, event.occurredAt)
    }

    @Test
    fun `exhaustive when over sealed interface covers both subtypes`() {
        val events: List<DomainEvent> = listOf(
            DomainEvent.SessionCompleted(
                sessionId = SessionId.generate(),
                agentId = AgentId.generate(),
                tenantId = TenantId.generate()
            ),
            DomainEvent.TenantPurged(tenantId = TenantId.generate())
        )

        val labels = events.map { event ->
            when (event) {
                is DomainEvent.SessionCompleted -> "session-completed"
                is DomainEvent.TenantPurged -> "tenant-purged"
            }
        }

        assertEquals(listOf("session-completed", "tenant-purged"), labels)
    }
}
