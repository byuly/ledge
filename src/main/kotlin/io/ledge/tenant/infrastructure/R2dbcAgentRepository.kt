package io.ledge.tenant.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.shared.AgentId
import io.ledge.shared.TenantId
import io.ledge.tenant.application.port.AgentRepository
import io.ledge.tenant.domain.Agent
import io.r2dbc.postgresql.codec.Json
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class R2dbcAgentRepository(
    private val db: DatabaseClient,
    private val objectMapper: ObjectMapper
) : AgentRepository {

    override fun save(agent: Agent): Agent {
        val metadataJson = objectMapper.writeValueAsString(agent.metadata)
        db.sql(
            """
            INSERT INTO agents (agent_id, tenant_id, name, description, metadata, created_at)
            VALUES (:id, :tenantId, :name, :description, :metadata, :createdAt)
            ON CONFLICT (agent_id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                metadata = EXCLUDED.metadata
            """.trimIndent()
        )
            .bind("id", agent.id.value)
            .bind("tenantId", agent.tenantId.value)
            .bind("name", agent.name)
            .bind("description", agent.description)
            .bind("metadata", Json.of(metadataJson))
            .bind("createdAt", agent.createdAt)
            .then()
            .block()
        return agent
    }

    override fun findById(id: AgentId, tenantId: TenantId): Agent? {
        return db.sql(
            """
            SELECT agent_id, tenant_id, name, description, metadata, created_at
            FROM agents WHERE agent_id = :id AND tenant_id = :tenantId
            """.trimIndent()
        )
            .bind("id", id.value)
            .bind("tenantId", tenantId.value)
            .map { row, _ -> mapRow(row) }
            .one()
            .block()
    }

    override fun findByTenantId(tenantId: TenantId): List<Agent> {
        return db.sql(
            """
            SELECT agent_id, tenant_id, name, description, metadata, created_at
            FROM agents WHERE tenant_id = :tenantId
            """.trimIndent()
        )
            .bind("tenantId", tenantId.value)
            .map { row, _ -> mapRow(row) }
            .all()
            .collectList()
            .block() ?: emptyList()
    }

    override fun deleteByTenantId(tenantId: TenantId) {
        db.sql("DELETE FROM agents WHERE tenant_id = :tenantId")
            .bind("tenantId", tenantId.value)
            .then()
            .block()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapRow(row: io.r2dbc.spi.Row): Agent {
        val metadataJson = row.get("metadata", Json::class.java)
        val metadata: Map<String, String> = if (metadataJson != null) {
            objectMapper.readValue(metadataJson.asString(), Map::class.java) as Map<String, String>
        } else {
            emptyMap()
        }
        return Agent(
            id = AgentId(row.get("agent_id", UUID::class.java)!!),
            tenantId = TenantId(row.get("tenant_id", UUID::class.java)!!),
            name = row.get("name", String::class.java)!!,
            description = row.get("description", String::class.java)!!,
            metadata = metadata,
            createdAt = row.get("created_at", Instant::class.java)!!
        )
    }
}
