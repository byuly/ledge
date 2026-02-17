package io.ledge.memory.infrastructure

import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.infrastructure.ClickHouseMemoryEventMapper
import io.ledge.memory.application.port.ObservationEventQuery
import io.ledge.shared.AgentId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant

@Component
class ClickHouseObservationEventQuery(
    @Value("\${clickhouse.url}") private val url: String
) : ObservationEventQuery {

    override fun findLatestContextAssembled(agentId: AgentId, tenantId: TenantId, before: Instant): MemoryEvent? {
        return DriverManager.getConnection(url).use { conn ->
            conn.prepareStatement(FIND_LATEST_CONTEXT_SQL).use { ps ->
                ps.setString(1, agentId.value.toString())
                ps.setString(2, tenantId.value.toString())
                ps.setTimestamp(3, Timestamp.from(before))

                val rs = ps.executeQuery()
                if (rs.next()) ClickHouseMemoryEventMapper.map(rs) else null
            }
        }
    }

    override fun findBySessionId(sessionId: SessionId, tenantId: TenantId): List<MemoryEvent> {
        return DriverManager.getConnection(url).use { conn ->
            conn.prepareStatement(FIND_BY_SESSION_SQL).use { ps ->
                ps.setString(1, sessionId.value.toString())
                ps.setString(2, tenantId.value.toString())

                val rs = ps.executeQuery()
                val results = mutableListOf<MemoryEvent>()
                while (rs.next()) {
                    results.add(ClickHouseMemoryEventMapper.map(rs))
                }
                results
            }
        }
    }

    companion object {
        private val FIND_LATEST_CONTEXT_SQL = """
            SELECT * FROM ledge.memory_events
            WHERE event_type = 'CONTEXT_ASSEMBLED'
              AND agent_id = ? AND tenant_id = ? AND occurred_at < ?
            ORDER BY occurred_at DESC LIMIT 1
        """.trimIndent()

        private val FIND_BY_SESSION_SQL = """
            SELECT * FROM ledge.memory_events
            WHERE session_id = ? AND tenant_id = ?
            ORDER BY sequence_number ASC
        """.trimIndent()
    }
}
