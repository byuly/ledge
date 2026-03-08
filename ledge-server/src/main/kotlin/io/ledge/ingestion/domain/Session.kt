package io.ledge.ingestion.domain

import io.ledge.shared.AgentId
import io.ledge.shared.EventId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import java.time.Instant

class Session(
    val id: SessionId,
    val agentId: AgentId,
    val tenantId: TenantId,
    val startedAt: Instant = Instant.now(),
    var endedAt: Instant? = null,
    var status: SessionStatus = SessionStatus.ACTIVE
) {
    private val _events = mutableListOf<MemoryEvent>()
    val events: List<MemoryEvent> get() = _events

    fun ingest(
        eventId: EventId,
        eventType: EventType,
        payload: String,
        occurredAt: Instant = Instant.now(),
        contextHash: ContextHash? = null,
        parentEventId: EventId? = null,
        schemaVersion: SchemaVersion = SchemaVersion(1)
    ): MemoryEvent {
        check(status == SessionStatus.ACTIVE) {
            "Cannot ingest events into session with status $status — session must be ACTIVE"
        }

        val event = MemoryEvent(
            id = eventId,
            sessionId = id,
            agentId = agentId,
            tenantId = tenantId,
            eventType = eventType,
            occurredAt = occurredAt,
            payload = payload,
            contextHash = contextHash,
            parentEventId = parentEventId,
            schemaVersion = schemaVersion
        )

        _events.add(event)
        return event
    }

    fun complete() {
        check(status == SessionStatus.ACTIVE) {
            "Cannot complete session with status $status — session must be ACTIVE"
        }
        status = SessionStatus.COMPLETED
        endedAt = Instant.now()
    }

    fun abandon() {
        check(status == SessionStatus.ACTIVE) {
            "Cannot abandon session with status $status — session must be ACTIVE"
        }
        status = SessionStatus.ABANDONED
        endedAt = Instant.now()
    }
}
