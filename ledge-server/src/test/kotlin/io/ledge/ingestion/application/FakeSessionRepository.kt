package io.ledge.ingestion.application

import io.ledge.ingestion.application.port.SessionRepository
import io.ledge.ingestion.domain.Session
import io.ledge.shared.AgentId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId

class FakeSessionRepository : SessionRepository {

    private val store = mutableMapOf<SessionId, Session>()

    override fun save(session: Session): Session {
        store[session.id] = session
        return session
    }

    override fun findById(id: SessionId, tenantId: TenantId): Session? =
        store[id]?.takeIf { it.tenantId == tenantId }

    override fun findByAgentId(agentId: AgentId, tenantId: TenantId): List<Session> =
        store.values.filter { it.agentId == agentId && it.tenantId == tenantId }

    override fun deleteByTenantId(tenantId: TenantId) {
        store.keys.removeAll { store[it]?.tenantId == tenantId }
    }

    override fun countActive(): Long =
        store.values.count { it.status == io.ledge.ingestion.domain.SessionStatus.ACTIVE }.toLong()
}
