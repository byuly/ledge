package io.ledge.tenant.infrastructure

import io.ledge.shared.TenantId
import io.ledge.tenant.application.port.TenantRepository
import io.ledge.tenant.domain.Tenant
import io.ledge.tenant.domain.TenantStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class R2dbcTenantRepository(
    private val db: DatabaseClient
) : TenantRepository {

    override fun save(tenant: Tenant): Tenant {
        db.sql(
            """
            INSERT INTO tenants (tenant_id, name, api_key_hash, status, created_at)
            VALUES (:id, :name, :apiKeyHash, :status, :createdAt)
            ON CONFLICT (tenant_id) DO UPDATE SET
                name = EXCLUDED.name,
                api_key_hash = EXCLUDED.api_key_hash,
                status = EXCLUDED.status
            """.trimIndent()
        )
            .bind("id", tenant.id.value)
            .bind("name", tenant.name)
            .bind("apiKeyHash", tenant.apiKeyHash)
            .bind("status", tenant.status.name)
            .bind("createdAt", tenant.createdAt)
            .then()
            .block()
        return tenant
    }

    override fun findById(id: TenantId): Tenant? {
        return db.sql("SELECT tenant_id, name, api_key_hash, status, created_at FROM tenants WHERE tenant_id = :id")
            .bind("id", id.value)
            .map { row, _ -> mapRow(row) }
            .one()
            .block()
    }

    override fun findByApiKeyHash(apiKeyHash: String): Tenant? {
        return db.sql("SELECT tenant_id, name, api_key_hash, status, created_at FROM tenants WHERE api_key_hash = :hash")
            .bind("hash", apiKeyHash)
            .map { row, _ -> mapRow(row) }
            .one()
            .block()
    }

    private fun mapRow(row: io.r2dbc.spi.Row): Tenant {
        return Tenant(
            id = TenantId(row.get("tenant_id", UUID::class.java)!!),
            name = row.get("name", String::class.java)!!,
            apiKeyHash = row.get("api_key_hash", String::class.java)!!,
            status = TenantStatus.valueOf(row.get("status", String::class.java)!!),
            createdAt = row.get("created_at", Instant::class.java)!!
        )
    }
}
