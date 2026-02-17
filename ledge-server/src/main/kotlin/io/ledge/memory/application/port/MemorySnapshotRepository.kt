package io.ledge.memory.application.port

import io.ledge.memory.domain.MemorySnapshot
import io.ledge.shared.AgentId
import io.ledge.shared.SnapshotId
import io.ledge.shared.TenantId

interface MemorySnapshotRepository {
    fun save(snapshot: MemorySnapshot): MemorySnapshot
    fun findById(id: SnapshotId): MemorySnapshot?
    fun findLatestByAgentId(agentId: AgentId, tenantId: TenantId): MemorySnapshot?
    fun deleteByTenantId(tenantId: TenantId)
}
