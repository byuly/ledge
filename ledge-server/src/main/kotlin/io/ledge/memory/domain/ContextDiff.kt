package io.ledge.memory.domain

import io.ledge.shared.SnapshotId

data class ContextDiff(
    val fromSnapshotId: SnapshotId,
    val toSnapshotId: SnapshotId,
    val addedEntries: List<MemoryEntry>,
    val removedEntries: List<MemoryEntry>,
    val modifiedEntries: List<MemoryEntryDelta>
)
