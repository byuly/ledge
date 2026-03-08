package io.ledge.ingestion.application

import io.ledge.ingestion.application.port.MemoryEventQuery
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import java.time.Instant

class FakeMemoryEventQuery : MemoryEventQuery {

    private val events = mutableListOf<MemoryEvent>()

    fun addEvent(event: MemoryEvent) {
        events.add(event)
    }

    override fun findBySessionId(
        sessionId: SessionId,
        tenantId: TenantId,
        after: Instant?,
        limit: Int
    ): List<MemoryEvent> =
        events
            .filter { it.sessionId == sessionId && it.tenantId == tenantId }
            .let { filtered ->
                if (after != null) {
                    filtered.filter { it.occurredAt.isAfter(after) }
                } else {
                    filtered
                }
            }
            .sortedBy { it.occurredAt }
            .take(limit)
}
