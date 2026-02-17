package io.ledge.ingestion.api

import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.domain.Session

data class CreateSessionRequest(
    val agentId: String
)

data class SessionResponse(
    val sessionId: String,
    val agentId: String,
    val tenantId: String,
    val startedAt: String,
    val endedAt: String?,
    val status: String,
    val nextSequenceNumber: Long
) {
    companion object {
        fun from(session: Session): SessionResponse = SessionResponse(
            sessionId = session.id.value.toString(),
            agentId = session.agentId.value.toString(),
            tenantId = session.tenantId.value.toString(),
            startedAt = session.startedAt.toString(),
            endedAt = session.endedAt?.toString(),
            status = session.status.name,
            nextSequenceNumber = session.nextSequenceNumber
        )
    }
}

data class UpdateSessionRequest(
    val status: String
)

data class MemoryEventResponse(
    val eventId: String,
    val sessionId: String,
    val agentId: String,
    val tenantId: String,
    val eventType: String,
    val sequenceNumber: Long,
    val occurredAt: String,
    val payload: String,
    val contextHash: String?,
    val parentEventId: String?,
    val schemaVersion: Int
) {
    companion object {
        fun from(event: MemoryEvent): MemoryEventResponse = MemoryEventResponse(
            eventId = event.id.value.toString(),
            sessionId = event.sessionId.value.toString(),
            agentId = event.agentId.value.toString(),
            tenantId = event.tenantId.value.toString(),
            eventType = event.eventType.name,
            sequenceNumber = event.sequenceNumber,
            occurredAt = event.occurredAt.toString(),
            payload = event.payload,
            contextHash = event.contextHash?.value,
            parentEventId = event.parentEventId?.value?.toString(),
            schemaVersion = event.schemaVersion.value
        )
    }
}
