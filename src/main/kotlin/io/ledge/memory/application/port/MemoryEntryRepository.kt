package io.ledge.memory.application.port

import io.ledge.memory.domain.MemoryEntry
import io.ledge.shared.EntryId
import io.ledge.shared.TenantId

interface MemoryEntryRepository {
    fun save(entry: MemoryEntry): MemoryEntry
    fun findById(id: EntryId): MemoryEntry?
    fun deleteByTenantId(tenantId: TenantId)
}
