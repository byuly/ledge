package io.ledge.ingestion.infrastructure

import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.shared.AgentId
import io.ledge.shared.EventId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import java.sql.ResultSet
import java.util.UUID

object ClickHouseMemoryEventMapper {

    fun map(rs: ResultSet): MemoryEvent {
        val contextHashStr = rs.getString("context_hash")
        val parentEventIdStr = rs.getString("parent_event_id")
        return MemoryEvent(
            id = EventId(UUID.fromString(rs.getString("event_id"))),
            sessionId = SessionId(UUID.fromString(rs.getString("session_id"))),
            agentId = AgentId(UUID.fromString(rs.getString("agent_id"))),
            tenantId = TenantId(UUID.fromString(rs.getString("tenant_id"))),
            eventType = EventType.valueOf(rs.getString("event_type")),
            sequenceNumber = rs.getLong("sequence_number"),
            occurredAt = rs.getTimestamp("occurred_at").toInstant(),
            payload = rs.getString("payload"),
            contextHash = if (contextHashStr.isNullOrEmpty()) null else ContextHash(contextHashStr),
            parentEventId = if (parentEventIdStr.isNullOrEmpty()) null else EventId(UUID.fromString(parentEventIdStr)),
            schemaVersion = SchemaVersion(rs.getInt("schema_version"))
        )
    }
}
