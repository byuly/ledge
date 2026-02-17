package io.ledge.memory.application

import io.ledge.memory.application.port.MemoryEntryRepository
import io.ledge.memory.domain.MemoryEntry
import io.ledge.shared.EntryId
import io.ledge.shared.TenantId

class FakeMemoryEntryRepository : MemoryEntryRepository {

    private val store = mutableMapOf<EntryId, MemoryEntry>()

    override fun save(entry: MemoryEntry): MemoryEntry {
        store[entry.id] = entry
        return entry
    }

    override fun findById(id: EntryId): MemoryEntry? = store[id]

    override fun deleteByTenantId(tenantId: TenantId) {
        store.keys.removeAll { store[it]?.let { e -> e.id in getEntryIdsForTenant(tenantId) } == true }
    }

    private var tenantEntries = mutableMapOf<TenantId, MutableSet<EntryId>>()

    fun associateWithTenant(entryId: EntryId, tenantId: TenantId) {
        tenantEntries.getOrPut(tenantId) { mutableSetOf() }.add(entryId)
    }

    private fun getEntryIdsForTenant(tenantId: TenantId): Set<EntryId> =
        tenantEntries[tenantId] ?: emptySet()
}
