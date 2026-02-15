package io.ledge.ingestion.application

import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.shared.EventId
import io.ledge.shared.SessionId
import java.time.Instant

data class IngestEventCommand(
    val sessionId: SessionId,
    val eventType: EventType,
    val payload: String,
    val occurredAt: Instant,
    val contextHash: ContextHash? = null,
    val parentEventId: EventId? = null,
    val schemaVersion: SchemaVersion = SchemaVersion(1)
)
