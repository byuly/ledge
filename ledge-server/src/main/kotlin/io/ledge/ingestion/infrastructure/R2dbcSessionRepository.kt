package io.ledge.ingestion.infrastructure

import io.ledge.ingestion.application.port.SessionRepository
import io.ledge.ingestion.domain.Session
import io.ledge.ingestion.domain.SessionStatus
import io.ledge.shared.AgentId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import io.r2dbc.postgresql.codec.Json
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class R2dbcSessionRepository(
    private val db: DatabaseClient
) : SessionRepository {

    override fun save(session: Session): Session {
        db.sql(
            """
            INSERT INTO sessions (session_id, agent_id, tenant_id, started_at, ended_at, status, metadata)
            VALUES (:id, :agentId, :tenantId, :startedAt, :endedAt, :status, :metadata)
            ON CONFLICT (session_id) DO UPDATE SET
                ended_at = EXCLUDED.ended_at,
                status = EXCLUDED.status
            """.trimIndent()
        )
            .bind("id", session.id.value)
            .bind("agentId", session.agentId.value)
            .bind("tenantId", session.tenantId.value)
            .bind("startedAt", session.startedAt)
            .bind("status", session.status.name)
            .bind("metadata", Json.of("{}"))
            .let { spec ->
                if (session.endedAt != null) {
                    spec.bind("endedAt", session.endedAt!!)
                } else {
                    spec.bindNull("endedAt", Instant::class.java)
                }
            }
            .then()
            .block()
        return session
    }

    override fun findById(id: SessionId, tenantId: TenantId): Session? {
        return db.sql(
            """
            SELECT session_id, agent_id, tenant_id, started_at, ended_at, status
            FROM sessions WHERE session_id = :id AND tenant_id = :tenantId
            """.trimIndent()
        )
            .bind("id", id.value)
            .bind("tenantId", tenantId.value)
            .map { row, _ -> mapRow(row) }
            .one()
            .block()
    }

    override fun findByAgentId(agentId: AgentId, tenantId: TenantId): List<Session> {
        return db.sql(
            """
            SELECT session_id, agent_id, tenant_id, started_at, ended_at, status
            FROM sessions WHERE agent_id = :agentId AND tenant_id = :tenantId
            """.trimIndent()
        )
            .bind("agentId", agentId.value)
            .bind("tenantId", tenantId.value)
            .map { row, _ -> mapRow(row) }
            .all()
            .collectList()
            .block() ?: emptyList()
    }

    override fun deleteByTenantId(tenantId: TenantId) {
        db.sql("DELETE FROM sessions WHERE tenant_id = :tenantId")
            .bind("tenantId", tenantId.value)
            .then()
            .block()
    }

    override fun countActive(): Long {
        return db.sql("SELECT COUNT(*) AS cnt FROM sessions WHERE status = 'ACTIVE'")
            .map { row, _ -> row.get("cnt", java.lang.Long::class.java)!!.toLong() }
            .one()
            .block() ?: 0L
    }

    private fun mapRow(row: io.r2dbc.spi.Row): Session {
        return Session(
            id = SessionId(row.get("session_id", UUID::class.java)!!),
            agentId = AgentId(row.get("agent_id", UUID::class.java)!!),
            tenantId = TenantId(row.get("tenant_id", UUID::class.java)!!),
            startedAt = row.get("started_at", Instant::class.java)!!,
            endedAt = row.get("ended_at", Instant::class.java),
            status = SessionStatus.valueOf(row.get("status", String::class.java)!!)
        )
    }
}
