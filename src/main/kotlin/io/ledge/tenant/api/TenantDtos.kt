package io.ledge.tenant.api

import io.ledge.tenant.domain.Agent
import io.ledge.tenant.domain.Tenant

data class CreateTenantRequest(
    val name: String,
    val apiKeyHash: String
)

data class TenantResponse(
    val tenantId: String,
    val name: String,
    val status: String,
    val createdAt: String
) {
    companion object {
        fun from(tenant: Tenant): TenantResponse = TenantResponse(
            tenantId = tenant.id.value.toString(),
            name = tenant.name,
            status = tenant.status.name,
            createdAt = tenant.createdAt.toString()
        )
    }
}

data class RegisterAgentRequest(
    val name: String,
    val description: String,
    val metadata: Map<String, String> = emptyMap()
)

data class AgentResponse(
    val agentId: String,
    val tenantId: String,
    val name: String,
    val description: String,
    val metadata: Map<String, String>,
    val createdAt: String
) {
    companion object {
        fun from(agent: Agent): AgentResponse = AgentResponse(
            agentId = agent.id.value.toString(),
            tenantId = agent.tenantId.value.toString(),
            name = agent.name,
            description = agent.description,
            metadata = agent.metadata,
            createdAt = agent.createdAt.toString()
        )
    }
}
