package io.ledge.ingestion.application.port

import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import java.time.Instant

interface MemoryEventQuery {
    fun findBySessionId(
        sessionId: SessionId,
        tenantId: TenantId,
        after: Instant? = null,
        limit: Int = 50
    ): List<MemoryEvent>
}
