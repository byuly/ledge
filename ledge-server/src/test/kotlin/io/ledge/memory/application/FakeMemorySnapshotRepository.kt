package io.ledge.memory.application

import io.ledge.memory.application.port.MemorySnapshotRepository
import io.ledge.memory.domain.MemorySnapshot
import io.ledge.shared.AgentId
import io.ledge.shared.SnapshotId
import io.ledge.shared.TenantId

class FakeMemorySnapshotRepository : MemorySnapshotRepository {

    private val store = mutableMapOf<SnapshotId, MemorySnapshot>()

    override fun save(snapshot: MemorySnapshot): MemorySnapshot {
        store[snapshot.id] = snapshot
        return snapshot
    }

    override fun findById(id: SnapshotId): MemorySnapshot? = store[id]

    override fun findLatestByAgentId(agentId: AgentId, tenantId: TenantId): MemorySnapshot? =
        store.values
            .filter { it.agentId == agentId && it.tenantId == tenantId }
            .maxByOrNull { it.createdAt }

    override fun deleteByTenantId(tenantId: TenantId) {
        store.keys.removeAll { store[it]?.tenantId == tenantId }
    }
}
