package io.ledge.memory.domain

import io.ledge.shared.EntryId

data class MemoryEntryDelta(
    val entryId: EntryId,
    val before: MemoryEntry,
    val after: MemoryEntry
)
