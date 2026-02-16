package io.ledge.memory.infrastructure

import io.ledge.memory.application.port.MemoryEntryRepository
import io.ledge.memory.domain.MemoryEntry
import io.ledge.shared.EntryId
import io.ledge.shared.TenantId
import org.springframework.stereotype.Component

@Component
class NoOpMemoryEntryRepository : MemoryEntryRepository {
    override fun save(entry: MemoryEntry): MemoryEntry = throw UnsupportedOperationException("v2")
    override fun findById(id: EntryId): MemoryEntry? = throw UnsupportedOperationException("v2")
    override fun deleteByTenantId(tenantId: TenantId): Unit = throw UnsupportedOperationException("v2")
}
