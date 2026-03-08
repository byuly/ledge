package io.ledge.ingestion.infrastructure

import io.ledge.ingestion.application.port.MemoryEventQuery
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant

@Component
class ClickHouseMemoryEventQuery(
    @Value("\${clickhouse.url}") private val url: String
) : MemoryEventQuery {

    override fun findBySessionId(
        sessionId: SessionId,
        tenantId: TenantId,
        after: Instant?,
        limit: Int
    ): List<MemoryEvent> {
        val hasTimeFilter = after != null
        val sql = buildString {
            append("SELECT * FROM ledge.memory_events WHERE session_id = ? AND tenant_id = ?")
            if (hasTimeFilter) append(" AND occurred_at > ?")
            append(" ORDER BY occurred_at ASC LIMIT ?")
        }

        return DriverManager.getConnection(url).use { conn ->
            conn.prepareStatement(sql).use { ps ->
                var idx = 1
                ps.setString(idx++, sessionId.value.toString())
                ps.setString(idx++, tenantId.value.toString())
                if (hasTimeFilter) ps.setTimestamp(idx++, Timestamp.from(after!!))
                ps.setInt(idx, limit)

                val rs = ps.executeQuery()
                val results = mutableListOf<MemoryEvent>()
                while (rs.next()) {
                    results.add(ClickHouseMemoryEventMapper.map(rs))
                }
                results
            }
        }
    }
}
