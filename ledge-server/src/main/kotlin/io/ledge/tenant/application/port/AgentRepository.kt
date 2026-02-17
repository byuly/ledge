package io.ledge.tenant.application.port

import io.ledge.shared.AgentId
import io.ledge.shared.TenantId
import io.ledge.tenant.domain.Agent

interface AgentRepository {
    fun save(agent: Agent): Agent
    fun findById(id: AgentId, tenantId: TenantId): Agent?
    fun findByTenantId(tenantId: TenantId): List<Agent>
    fun deleteByTenantId(tenantId: TenantId)
}
