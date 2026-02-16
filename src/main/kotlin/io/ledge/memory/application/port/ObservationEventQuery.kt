package io.ledge.memory.application.port

import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.shared.AgentId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import java.time.Instant

interface ObservationEventQuery {
    fun findLatestContextAssembled(agentId: AgentId, tenantId: TenantId, before: Instant): MemoryEvent?
    fun findBySessionId(sessionId: SessionId, tenantId: TenantId): List<MemoryEvent>
}
