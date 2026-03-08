package io.ledge.ingestion.infrastructure

import io.ledge.ingestion.domain.MemoryEvent
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.sql.DriverManager
import java.sql.Timestamp
import java.sql.Types

@Component
class ClickHouseMemoryEventWriter(
    @Value("\${clickhouse.url}") private val url: String,
    private val meterRegistry: MeterRegistry
) {

    fun write(event: MemoryEvent) {
        writeAll(listOf(event))
    }

    fun writeAll(events: List<MemoryEvent>) {
        if (events.isEmpty()) return

        meterRegistry.timer("ledge.clickhouse.write.duration").record(Runnable {
            DriverManager.getConnection(url).use { conn ->
                conn.prepareStatement(INSERT_SQL).use { ps ->
                    for (event in events) {
                        ps.setString(1, event.id.value.toString())
                        ps.setString(2, event.sessionId.value.toString())
                        ps.setString(3, event.agentId.value.toString())
                        ps.setString(4, event.tenantId.value.toString())
                        ps.setString(5, event.eventType.name)
                        ps.setTimestamp(6, Timestamp.from(event.occurredAt))
                        ps.setString(7, event.payload)
                        ps.setString(8, event.contextHash?.value ?: "")
                        if (event.parentEventId != null) {
                            ps.setString(9, event.parentEventId.value.toString())
                        } else {
                            ps.setNull(9, Types.OTHER)
                        }
                        ps.setInt(10, event.schemaVersion.value)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
        })
    }

    companion object {
        private val INSERT_SQL = """
            INSERT INTO ledge.memory_events
            (event_id, session_id, agent_id, tenant_id, event_type,
             occurred_at, payload, context_hash, parent_event_id, schema_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    }
}
