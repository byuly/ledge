package io.ledge

import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.memory.domain.Confidence
import io.ledge.memory.domain.ContentHash
import io.ledge.memory.domain.MemoryEntry
import io.ledge.memory.domain.MemoryEntryType
import io.ledge.shared.*
import java.time.Instant

object TestFixtures {

    const val VALID_SHA256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"

    fun tenantId(): TenantId = TenantId.generate()
    fun agentId(): AgentId = AgentId.generate()
    fun sessionId(): SessionId = SessionId.generate()
    fun eventId(): EventId = EventId.generate()
    fun snapshotId(): SnapshotId = SnapshotId.generate()
    fun entryId(): EntryId = EntryId.generate()

    fun contextHash(): ContextHash = ContextHash(VALID_SHA256)
    fun contentHash(): ContentHash = ContentHash(VALID_SHA256)
    fun confidence(value: Float = 0.8f): Confidence = Confidence(value)
    fun schemaVersion(value: Int = 1): SchemaVersion = SchemaVersion(value)

    fun memoryEntry(
        id: EntryId = entryId(),
        content: String = "The user prefers dark mode",
        contentHash: ContentHash = contentHash(),
        entryType: MemoryEntryType = MemoryEntryType.PREFERENCE,
        confidence: Confidence = confidence(),
        sourceEventId: EventId = eventId(),
        createdAt: Instant = Instant.now(),
        expiresAt: Instant? = null,
        accessCount: Long = 0,
        lastAccessedAt: Instant? = null
    ): MemoryEntry = MemoryEntry(
        id = id,
        content = content,
        contentHash = contentHash,
        entryType = entryType,
        confidence = confidence,
        sourceEventId = sourceEventId,
        createdAt = createdAt,
        expiresAt = expiresAt,
        accessCount = accessCount,
        lastAccessedAt = lastAccessedAt
    )
}
