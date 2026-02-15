package io.ledge.tenant.application

import io.ledge.shared.TenantId
import io.ledge.tenant.application.port.TenantRepository
import io.ledge.tenant.domain.Tenant

class FakeTenantRepository : TenantRepository {

    private val store = mutableMapOf<TenantId, Tenant>()

    override fun save(tenant: Tenant): Tenant {
        store[tenant.id] = tenant
        return tenant
    }

    override fun findById(id: TenantId): Tenant? = store[id]

    override fun findByApiKeyHash(apiKeyHash: String): Tenant? =
        store.values.find { it.apiKeyHash == apiKeyHash }
}
