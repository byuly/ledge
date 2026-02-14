package io.ledge.memory.domain

import io.ledge.TestFixtures
import org.junit.jupiter.api.Test
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class MemoryEntryTest {

    @Test
    fun `construction with required fields`() {
        val id = TestFixtures.entryId()
        val contentHash = TestFixtures.contentHash()
        val confidence = TestFixtures.confidence()
        val sourceEventId = TestFixtures.eventId()

        val entry = MemoryEntry(
            id = id,
            content = "User prefers dark mode",
            contentHash = contentHash,
            entryType = MemoryEntryType.PREFERENCE,
            confidence = confidence,
            sourceEventId = sourceEventId
        )

        assertEquals(id, entry.id)
        assertEquals("User prefers dark mode", entry.content)
        assertEquals(contentHash, entry.contentHash)
        assertEquals(MemoryEntryType.PREFERENCE, entry.entryType)
        assertEquals(confidence, entry.confidence)
        assertEquals(sourceEventId, entry.sourceEventId)
    }

    @Test
    fun `default accessCount is 0`() {
        val entry = TestFixtures.memoryEntry()
        assertEquals(0L, entry.accessCount)
    }

    @Test
    fun `default expiresAt and lastAccessedAt are null`() {
        val entry = TestFixtures.memoryEntry()
        assertNull(entry.expiresAt)
        assertNull(entry.lastAccessedAt)
    }

    @Test
    fun `data class equality`() {
        val id = TestFixtures.entryId()
        val contentHash = TestFixtures.contentHash()
        val confidence = TestFixtures.confidence()
        val sourceEventId = TestFixtures.eventId()
        val createdAt = Instant.parse("2025-01-01T00:00:00Z")

        val entry1 = MemoryEntry(
            id = id, content = "fact", contentHash = contentHash,
            entryType = MemoryEntryType.FACT, confidence = confidence,
            sourceEventId = sourceEventId, createdAt = createdAt
        )
        val entry2 = MemoryEntry(
            id = id, content = "fact", contentHash = contentHash,
            entryType = MemoryEntryType.FACT, confidence = confidence,
            sourceEventId = sourceEventId, createdAt = createdAt
        )

        assertEquals(entry1, entry2)
    }
}
