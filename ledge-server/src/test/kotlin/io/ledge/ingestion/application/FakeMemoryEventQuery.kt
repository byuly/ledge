package io.ledge.ingestion.application

import io.ledge.ingestion.application.port.MemoryEventQuery
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId

class FakeMemoryEventQuery : MemoryEventQuery {

    private val events = mutableListOf<MemoryEvent>()

    fun addEvent(event: MemoryEvent) {
        events.add(event)
    }

    override fun findBySessionId(
        sessionId: SessionId,
        tenantId: TenantId,
        afterSequenceNumber: Long?,
        limit: Int
    ): List<MemoryEvent> =
        events
            .filter { it.sessionId == sessionId && it.tenantId == tenantId }
            .let { filtered ->
                if (afterSequenceNumber != null) {
                    filtered.filter { it.sequenceNumber > afterSequenceNumber }
                } else {
                    filtered
                }
            }
            .sortedBy { it.sequenceNumber }
            .take(limit)
}
