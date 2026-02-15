package io.ledge.memory.application

import io.ledge.memory.application.port.DomainEventPublisher
import io.ledge.memory.application.port.MemoryEntryRepository
import io.ledge.memory.application.port.MemorySnapshotRepository
import io.ledge.memory.domain.ContentHash
import io.ledge.memory.domain.ContextDiff
import io.ledge.memory.domain.MemoryEntry
import io.ledge.memory.domain.MemoryEntryDelta
import io.ledge.memory.domain.MemorySnapshot
import io.ledge.shared.AgentId
import io.ledge.shared.DomainEvent
import io.ledge.shared.EntryId
import io.ledge.shared.EventId
import io.ledge.shared.SnapshotId
import io.ledge.shared.TenantId
import java.time.Instant

class MemoryService(
    private val snapshotRepository: MemorySnapshotRepository,
    private val entryRepository: MemoryEntryRepository,
    private val eventPublisher: DomainEventPublisher
) {
    fun createSnapshot(
        agentId: AgentId,
        tenantId: TenantId,
        entries: List<MemoryEntry>,
        snapshotHash: ContentHash,
        triggerEventId: EventId
    ): MemorySnapshot {
        val parentSnapshot = snapshotRepository.findLatestByAgentId(agentId, tenantId)
        val snapshot = MemorySnapshot(
            id = SnapshotId.generate(),
            agentId = agentId,
            tenantId = tenantId,
            parentSnapshotId = parentSnapshot?.id,
            snapshotHash = snapshotHash,
            entries = entries,
            triggerEventId = triggerEventId
        )
        return snapshotRepository.save(snapshot)
    }

    fun getSnapshot(snapshotId: SnapshotId): MemorySnapshot? {
        return snapshotRepository.findById(snapshotId)
    }

    fun getLatestSnapshot(agentId: AgentId, tenantId: TenantId): MemorySnapshot? {
        return snapshotRepository.findLatestByAgentId(agentId, tenantId)
    }

    fun diffSnapshots(fromSnapshotId: SnapshotId, toSnapshotId: SnapshotId): ContextDiff {
        val from = snapshotRepository.findById(fromSnapshotId)
            ?: throw IllegalArgumentException("Snapshot not found: $fromSnapshotId")
        val to = snapshotRepository.findById(toSnapshotId)
            ?: throw IllegalArgumentException("Snapshot not found: $toSnapshotId")

        val fromEntriesById = from.entries.associateBy { it.id }
        val toEntriesById = to.entries.associateBy { it.id }

        val added = to.entries.filter { it.id !in fromEntriesById }
        val removed = from.entries.filter { it.id !in toEntriesById }
        val modified = to.entries.mapNotNull { toEntry ->
            val fromEntry = fromEntriesById[toEntry.id]
            if (fromEntry != null && fromEntry != toEntry) {
                MemoryEntryDelta(entryId = toEntry.id, before = fromEntry, after = toEntry)
            } else {
                null
            }
        }

        return ContextDiff(
            fromSnapshotId = fromSnapshotId,
            toSnapshotId = toSnapshotId,
            addedEntries = added,
            removedEntries = removed,
            modifiedEntries = modified
        )
    }

    fun recordAccess(entryId: EntryId): MemoryEntry? {
        val entry = entryRepository.findById(entryId) ?: return null
        val updated = entry.copy(
            accessCount = entry.accessCount + 1,
            lastAccessedAt = Instant.now()
        )
        return entryRepository.save(updated)
    }

    fun purgeTenantMemory(tenantId: TenantId) {
        entryRepository.deleteByTenantId(tenantId)
        snapshotRepository.deleteByTenantId(tenantId)
        eventPublisher.publish(DomainEvent.TenantPurged(tenantId = tenantId))
    }
}
