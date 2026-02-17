package io.ledge.ingestion.application.port

import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId

interface MemoryEventQuery {
    fun findBySessionId(
        sessionId: SessionId,
        tenantId: TenantId,
        afterSequenceNumber: Long? = null,
        limit: Int = 50
    ): List<MemoryEvent>
}
