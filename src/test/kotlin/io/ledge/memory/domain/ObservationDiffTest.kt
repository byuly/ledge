package io.ledge.memory.domain

import io.ledge.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObservationDiffTest {

    @Test
    fun `construction with added, removed, and modified blocks`() {
        val addedBlock = TestFixtures.contentBlock(blockType = "document", content = "new doc")
        val removedBlock = TestFixtures.contentBlock(blockType = "tool_result", content = "old result")
        val delta = TestFixtures.contentBlockDelta(blockType = "user")

        val diff = ObservationDiff(
            fromEventId = TestFixtures.eventId(),
            toEventId = TestFixtures.eventId(),
            addedBlocks = listOf(addedBlock),
            removedBlocks = listOf(removedBlock),
            modifiedBlocks = listOf(delta)
        )

        assertEquals(1, diff.addedBlocks.size)
        assertEquals(1, diff.removedBlocks.size)
        assertEquals(1, diff.modifiedBlocks.size)
        assertEquals("document", diff.addedBlocks[0].blockType)
        assertEquals("tool_result", diff.removedBlocks[0].blockType)
        assertEquals("user", diff.modifiedBlocks[0].blockType)
    }

    @Test
    fun `empty diff has no changes`() {
        val diff = TestFixtures.observationDiff()

        assertTrue(diff.addedBlocks.isEmpty())
        assertTrue(diff.removedBlocks.isEmpty())
        assertTrue(diff.modifiedBlocks.isEmpty())
    }

    @Test
    fun `data class equality`() {
        val fromEventId = TestFixtures.eventId()
        val toEventId = TestFixtures.eventId()

        val a = ObservationDiff(
            fromEventId = fromEventId,
            toEventId = toEventId,
            addedBlocks = emptyList(),
            removedBlocks = emptyList(),
            modifiedBlocks = emptyList()
        )
        val b = ObservationDiff(
            fromEventId = fromEventId,
            toEventId = toEventId,
            addedBlocks = emptyList(),
            removedBlocks = emptyList(),
            modifiedBlocks = emptyList()
        )
        val c = ObservationDiff(
            fromEventId = fromEventId,
            toEventId = toEventId,
            addedBlocks = listOf(TestFixtures.contentBlock()),
            removedBlocks = emptyList(),
            modifiedBlocks = emptyList()
        )

        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
