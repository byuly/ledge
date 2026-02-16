package io.ledge.memory.infrastructure

import io.ledge.memory.application.port.MemorySnapshotRepository
import io.ledge.memory.domain.MemorySnapshot
import io.ledge.shared.AgentId
import io.ledge.shared.SnapshotId
import io.ledge.shared.TenantId
import org.springframework.stereotype.Component

@Component
class NoOpMemorySnapshotRepository : MemorySnapshotRepository {
    override fun save(snapshot: MemorySnapshot): MemorySnapshot = throw UnsupportedOperationException("v2")
    override fun findById(id: SnapshotId): MemorySnapshot? = throw UnsupportedOperationException("v2")
    override fun findLatestByAgentId(agentId: AgentId, tenantId: TenantId): MemorySnapshot? = throw UnsupportedOperationException("v2")
    override fun deleteByTenantId(tenantId: TenantId): Unit = throw UnsupportedOperationException("v2")
}
