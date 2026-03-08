package io.ledge.ingestion.application.port

import io.ledge.ingestion.domain.Session
import io.ledge.shared.AgentId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId

interface SessionRepository {
    fun save(session: Session): Session
    fun findById(id: SessionId, tenantId: TenantId): Session?
    fun findByAgentId(agentId: AgentId, tenantId: TenantId): List<Session>
    fun deleteByTenantId(tenantId: TenantId)
    fun countActive(): Long
}
