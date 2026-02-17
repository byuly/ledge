package io.ledge.memory.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ledge.memory.application.port.DomainEventPublisher
import io.ledge.memory.application.port.MemoryEntryRepository
import io.ledge.memory.application.port.MemorySnapshotRepository
import io.ledge.memory.application.port.ObservationEventQuery
import io.ledge.memory.domain.ContentBlock
import io.ledge.memory.domain.ContentBlockDelta
import io.ledge.memory.domain.ContentHash
import io.ledge.memory.domain.ContextDiff
import io.ledge.memory.domain.MemoryEntry
import io.ledge.memory.domain.MemoryEntryDelta
import io.ledge.memory.domain.MemorySnapshot
import io.ledge.memory.domain.ObservationDiff
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.shared.AgentId
import io.ledge.shared.DomainEvent
import io.ledge.shared.EntryId
import io.ledge.shared.EventId
import io.ledge.shared.SessionId
import io.ledge.shared.SnapshotId
import io.ledge.shared.TenantId
import java.time.Instant

class MemoryService(
    private val snapshotRepository: MemorySnapshotRepository,
    private val entryRepository: MemoryEntryRepository,
    private val eventPublisher: DomainEventPublisher,
    private val observationEventQuery: ObservationEventQuery
) {
    private val objectMapper = jacksonObjectMapper()

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

    // --- Observation Layer (v1) ---

    fun getContextAtTime(agentId: AgentId, tenantId: TenantId, timestamp: Instant): MemoryEvent? {
        return observationEventQuery.findLatestContextAssembled(agentId, tenantId, timestamp)
    }

    fun diffContextWindows(agentId: AgentId, tenantId: TenantId, from: Instant, to: Instant): ObservationDiff {
        val fromEvent = observationEventQuery.findLatestContextAssembled(agentId, tenantId, from)
            ?: throw IllegalArgumentException("No CONTEXT_ASSEMBLED event found before: $from")
        val toEvent = observationEventQuery.findLatestContextAssembled(agentId, tenantId, to)
            ?: throw IllegalArgumentException("No CONTEXT_ASSEMBLED event found before: $to")

        val fromBlocks = parseContentBlocks(fromEvent.payload)
        val toBlocks = parseContentBlocks(toEvent.payload)

        return computeBlockDiff(fromEvent.id, toEvent.id, fromBlocks, toBlocks)
    }

    fun getAuditTrail(sessionId: SessionId, tenantId: TenantId): List<MemoryEvent> {
        return observationEventQuery.findBySessionId(sessionId, tenantId)
    }

    private fun parseContentBlocks(payload: String): List<ContentBlock> {
        return objectMapper.readValue(payload)
    }

    private fun computeBlockDiff(
        fromEventId: EventId,
        toEventId: EventId,
        fromBlocks: List<ContentBlock>,
        toBlocks: List<ContentBlock>
    ): ObservationDiff {
        val fromByType = fromBlocks.groupBy { it.blockType }
        val toByType = toBlocks.groupBy { it.blockType }

        val allTypes = fromByType.keys + toByType.keys

        val added = mutableListOf<ContentBlock>()
        val removed = mutableListOf<ContentBlock>()
        val modified = mutableListOf<ContentBlockDelta>()

        for (type in allTypes) {
            val fromGroup = fromByType[type] ?: emptyList()
            val toGroup = toByType[type] ?: emptyList()

            val maxLen = maxOf(fromGroup.size, toGroup.size)
            for (i in 0 until maxLen) {
                val fromBlock = fromGroup.getOrNull(i)
                val toBlock = toGroup.getOrNull(i)

                when {
                    fromBlock == null && toBlock != null -> added.add(toBlock)
                    fromBlock != null && toBlock == null -> removed.add(fromBlock)
                    fromBlock != null && toBlock != null && fromBlock != toBlock ->
                        modified.add(ContentBlockDelta(blockType = type, before = fromBlock, after = toBlock))
                }
            }
        }

        return ObservationDiff(
            fromEventId = fromEventId,
            toEventId = toEventId,
            addedBlocks = added,
            removedBlocks = removed,
            modifiedBlocks = modified
        )
    }
}
