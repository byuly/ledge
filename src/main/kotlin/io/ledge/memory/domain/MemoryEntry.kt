package io.ledge.memory.domain

import io.ledge.shared.EntryId
import io.ledge.shared.EventId
import java.time.Instant

data class MemoryEntry(
    val id: EntryId,
    val content: String,
    val contentHash: ContentHash,
    val entryType: MemoryEntryType,
    val confidence: Confidence,
    val sourceEventId: EventId,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val accessCount: Long = 0,
    val lastAccessedAt: Instant? = null
)
