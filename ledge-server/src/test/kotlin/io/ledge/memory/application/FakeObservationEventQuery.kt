package io.ledge.memory.application

import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.memory.application.port.ObservationEventQuery
import io.ledge.shared.AgentId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import java.time.Instant

class FakeObservationEventQuery : ObservationEventQuery {

    private val events = mutableListOf<MemoryEvent>()

    fun addEvent(event: MemoryEvent) {
        events.add(event)
    }

    override fun findLatestContextAssembled(agentId: AgentId, tenantId: TenantId, before: Instant): MemoryEvent? =
        events
            .filter {
                it.agentId == agentId &&
                    it.tenantId == tenantId &&
                    it.eventType == EventType.CONTEXT_ASSEMBLED &&
                    it.occurredAt.isBefore(before)
            }
            .maxByOrNull { it.occurredAt }

    override fun findBySessionId(sessionId: SessionId, tenantId: TenantId): List<MemoryEvent> =
        events
            .filter { it.sessionId == sessionId && it.tenantId == tenantId }
            .sortedBy { it.occurredAt }
}
