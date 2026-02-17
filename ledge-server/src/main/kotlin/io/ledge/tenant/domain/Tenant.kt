package io.ledge.tenant.domain

import io.ledge.shared.TenantId
import java.time.Instant

class Tenant(
    val id: TenantId,
    val name: String,
    val apiKeyHash: String,
    var status: TenantStatus = TenantStatus.ACTIVE,
    val createdAt: Instant = Instant.now()
) {
    fun suspend() {
        check(status == TenantStatus.ACTIVE) {
            "Cannot suspend tenant in status $status — only ACTIVE tenants can be suspended"
        }
        status = TenantStatus.SUSPENDED
    }

    fun delete() {
        check(status != TenantStatus.DELETED) {
            "Tenant is already deleted"
        }
        status = TenantStatus.DELETED
    }
}
