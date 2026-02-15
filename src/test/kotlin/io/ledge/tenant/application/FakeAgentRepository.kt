package io.ledge.tenant.application

import io.ledge.shared.AgentId
import io.ledge.shared.TenantId
import io.ledge.tenant.application.port.AgentRepository
import io.ledge.tenant.domain.Agent

class FakeAgentRepository : AgentRepository {

    private val store = mutableMapOf<AgentId, Agent>()

    override fun save(agent: Agent): Agent {
        store[agent.id] = agent
        return agent
    }

    override fun findById(id: AgentId, tenantId: TenantId): Agent? =
        store[id]?.takeIf { it.tenantId == tenantId }

    override fun findByTenantId(tenantId: TenantId): List<Agent> =
        store.values.filter { it.tenantId == tenantId }

    override fun deleteByTenantId(tenantId: TenantId) {
        store.keys.removeAll { store[it]?.tenantId == tenantId }
    }
}
