package io.ledge.ingestion.domain

import io.ledge.shared.AgentId
import io.ledge.shared.EventId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import java.time.Instant

data class MemoryEvent(
    val id: EventId,
    val sessionId: SessionId,
    val agentId: AgentId,
    val tenantId: TenantId,
    val eventType: EventType,
    val occurredAt: Instant,
    val payload: String,
    val contextHash: ContextHash?,
    val parentEventId: EventId?,
    val schemaVersion: SchemaVersion
)
