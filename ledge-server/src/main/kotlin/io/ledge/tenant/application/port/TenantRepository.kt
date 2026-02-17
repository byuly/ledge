package io.ledge.tenant.application.port

import io.ledge.shared.TenantId
import io.ledge.tenant.domain.Tenant

interface TenantRepository {
    fun save(tenant: Tenant): Tenant
    fun findById(id: TenantId): Tenant?
    fun findByApiKeyHash(apiKeyHash: String): Tenant?
}
