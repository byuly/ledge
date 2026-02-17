package io.ledge.tenant.application

import io.ledge.shared.AgentId
import io.ledge.shared.DomainEvent
import io.ledge.shared.TenantId
import io.ledge.tenant.application.port.AgentRepository
import io.ledge.tenant.application.port.DomainEventPublisher
import io.ledge.tenant.application.port.TenantRepository
import io.ledge.tenant.domain.Agent
import io.ledge.tenant.domain.Tenant

class TenantService(
    private val tenantRepository: TenantRepository,
    private val agentRepository: AgentRepository,
    private val domainEventPublisher: DomainEventPublisher
) {

    fun createTenant(command: CreateTenantCommand): Tenant {
        val tenant = Tenant(
            id = TenantId.generate(),
            name = command.name,
            apiKeyHash = command.apiKeyHash
        )
        return tenantRepository.save(tenant)
    }

    fun getTenant(tenantId: TenantId): Tenant? {
        return tenantRepository.findById(tenantId)
    }

    fun suspendTenant(tenantId: TenantId): Tenant {
        val tenant = tenantRepository.findById(tenantId)
            ?: throw IllegalArgumentException("Tenant not found: $tenantId")
        tenant.suspend()
        return tenantRepository.save(tenant)
    }

    fun deleteTenant(tenantId: TenantId): Tenant {
        val tenant = tenantRepository.findById(tenantId)
            ?: throw IllegalArgumentException("Tenant not found: $tenantId")
        tenant.delete()
        tenantRepository.save(tenant)
        agentRepository.deleteByTenantId(tenantId)
        domainEventPublisher.publish(DomainEvent.TenantPurged(tenantId = tenantId))
        return tenant
    }

    fun registerAgent(tenantId: TenantId, command: RegisterAgentCommand): Agent {
        val tenant = tenantRepository.findById(tenantId)
            ?: throw IllegalArgumentException("Tenant not found: $tenantId")
        check(tenant.status == io.ledge.tenant.domain.TenantStatus.ACTIVE) {
            "Cannot register agent for tenant in status ${tenant.status} — only ACTIVE tenants accept new agents"
        }
        val agent = Agent(
            id = AgentId.generate(),
            tenantId = tenantId,
            name = command.name,
            description = command.description,
            metadata = command.metadata
        )
        return agentRepository.save(agent)
    }

    fun getAgent(agentId: AgentId, tenantId: TenantId): Agent? {
        return agentRepository.findById(agentId, tenantId)
    }

    fun listAgents(tenantId: TenantId): List<Agent> {
        return agentRepository.findByTenantId(tenantId)
    }
}
