package io.ledge.memory.domain

import io.ledge.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ContentBlockTest {

    @Test
    fun `construction with all fields`() {
        val block = ContentBlock(
            blockType = "system",
            content = "You are a helpful assistant",
            tokenCount = 6,
            source = "system-prompt"
        )

        assertEquals("system", block.blockType)
        assertEquals("You are a helpful assistant", block.content)
        assertEquals(6, block.tokenCount)
        assertEquals("system-prompt", block.source)
    }

    @Test
    fun `construction with null source`() {
        val block = TestFixtures.contentBlock(source = null)

        assertNull(block.source)
    }

    @Test
    fun `data class equality`() {
        val a = ContentBlock(blockType = "user", content = "hello", tokenCount = 1, source = null)
        val b = ContentBlock(blockType = "user", content = "hello", tokenCount = 1, source = null)
        val c = ContentBlock(blockType = "user", content = "world", tokenCount = 1, source = null)

        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
