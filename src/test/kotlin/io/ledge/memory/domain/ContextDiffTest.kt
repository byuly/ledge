package io.ledge.memory.domain

import io.ledge.TestFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ContextDiffTest {

    @Test
    fun `construction with added, removed, and modified entries`() {
        val fromSnapshot = TestFixtures.snapshotId()
        val toSnapshot = TestFixtures.snapshotId()
        val added = listOf(TestFixtures.memoryEntry())
        val removed = listOf(TestFixtures.memoryEntry())
        val entryId = TestFixtures.entryId()
        val before = TestFixtures.memoryEntry(id = entryId, content = "old")
        val after = TestFixtures.memoryEntry(id = entryId, content = "new")
        val modified = listOf(MemoryEntryDelta(entryId = entryId, before = before, after = after))

        val diff = ContextDiff(
            fromSnapshotId = fromSnapshot,
            toSnapshotId = toSnapshot,
            addedEntries = added,
            removedEntries = removed,
            modifiedEntries = modified
        )

        assertEquals(fromSnapshot, diff.fromSnapshotId)
        assertEquals(toSnapshot, diff.toSnapshotId)
        assertEquals(1, diff.addedEntries.size)
        assertEquals(1, diff.removedEntries.size)
        assertEquals(1, diff.modifiedEntries.size)
        assertEquals("old", diff.modifiedEntries[0].before.content)
        assertEquals("new", diff.modifiedEntries[0].after.content)
    }

    @Test
    fun `empty diff with no changes`() {
        val diff = ContextDiff(
            fromSnapshotId = TestFixtures.snapshotId(),
            toSnapshotId = TestFixtures.snapshotId(),
            addedEntries = emptyList(),
            removedEntries = emptyList(),
            modifiedEntries = emptyList()
        )

        assertTrue(diff.addedEntries.isEmpty())
        assertTrue(diff.removedEntries.isEmpty())
        assertTrue(diff.modifiedEntries.isEmpty())
    }
}
