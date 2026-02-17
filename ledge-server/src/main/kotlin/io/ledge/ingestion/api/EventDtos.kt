package io.ledge.ingestion.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.ingestion.application.IngestEventCommand
import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.shared.EventId
import io.ledge.shared.SessionId
import java.time.Instant

data class IngestEventRequest(
    val sessionId: String,
    val agentId: String,
    val eventType: String,
    val occurredAt: String,
    val payload: JsonNode,
    val contextHash: String? = null,
    val parentEventId: String? = null,
    val schemaVersion: Int = 1
) {
    fun toCommand(objectMapper: ObjectMapper): IngestEventCommand = IngestEventCommand(
        sessionId = SessionId.fromString(sessionId),
        eventType = EventType.valueOf(eventType),
        payload = objectMapper.writeValueAsString(payload),
        occurredAt = Instant.parse(occurredAt),
        contextHash = contextHash?.let { ContextHash(it) },
        parentEventId = parentEventId?.let { EventId.fromString(it) },
        schemaVersion = SchemaVersion(schemaVersion)
    )
}

data class IngestEventResponse(
    val eventId: String,
    val sequenceNumber: Long
)

data class IngestBatchRequest(
    val events: List<IngestEventRequest>
)

data class IngestBatchResponse(
    val accepted: Int,
    val results: List<IngestEventResponse>
)
