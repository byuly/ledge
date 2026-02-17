package io.ledge.shared

import java.util.UUID

@JvmInline
value class TenantId(val value: UUID) {
    companion object {
        fun generate(): TenantId = TenantId(UUID.randomUUID())
        fun fromString(id: String): TenantId = TenantId(UUID.fromString(id))
    }

    override fun toString(): String = value.toString()
}
