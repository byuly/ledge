package io.ledge.infrastructure.kafka

import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.shared.AgentId
import io.ledge.shared.EventId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import java.time.Instant

data class MemoryEventEnvelope(
    val eventId: String,
    val sessionId: String,
    val agentId: String,
    val tenantId: String,
    val eventType: String,
    val occurredAt: String,
    val payload: String,
    val contextHash: String?,
    val parentEventId: String?,
    val schemaVersion: Int
) {
    companion object {
        fun from(event: MemoryEvent): MemoryEventEnvelope = MemoryEventEnvelope(
            eventId = event.id.value.toString(),
            sessionId = event.sessionId.value.toString(),
            agentId = event.agentId.value.toString(),
            tenantId = event.tenantId.value.toString(),
            eventType = event.eventType.name,
            occurredAt = event.occurredAt.toString(),
            payload = event.payload,
            contextHash = event.contextHash?.value,
            parentEventId = event.parentEventId?.value?.toString(),
            schemaVersion = event.schemaVersion.value
        )
    }

    fun toMemoryEvent(): MemoryEvent = MemoryEvent(
        id = EventId.fromString(eventId),
        sessionId = SessionId.fromString(sessionId),
        agentId = AgentId.fromString(agentId),
        tenantId = TenantId.fromString(tenantId),
        eventType = EventType.valueOf(eventType),
        occurredAt = Instant.parse(occurredAt),
        payload = payload,
        contextHash = contextHash?.let { ContextHash(it) },
        parentEventId = parentEventId?.let { EventId.fromString(it) },
        schemaVersion = SchemaVersion(schemaVersion)
    )
}
