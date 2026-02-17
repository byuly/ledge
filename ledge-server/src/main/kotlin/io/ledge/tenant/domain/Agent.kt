package io.ledge.tenant.domain

import io.ledge.shared.AgentId
import io.ledge.shared.TenantId
import java.time.Instant

data class Agent(
    val id: AgentId,
    val tenantId: TenantId,
    val name: String,
    val description: String,
    val createdAt: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
)
