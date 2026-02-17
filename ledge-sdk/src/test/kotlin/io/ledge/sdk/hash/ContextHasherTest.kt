package io.ledge.sdk.hash

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContextHasherTest {

    @Test
    fun `sha256 produces 64-char lowercase hex string`() {
        val hash = ContextHasher.sha256("hello world")
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[a-f0-9]{64}$")))
    }

    @Test
    fun `sha256 matches known vector`() {
        // SHA-256 of empty string
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ContextHasher.sha256("")
        )
    }

    @Test
    fun `sha256 matches known vector for hello world`() {
        assertEquals(
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
            ContextHasher.sha256("hello world")
        )
    }

    @Test
    fun `hashContentBlocks produces deterministic hash`() {
        val blocks = listOf(
            mapOf<String, Any?>("role" to "system", "content" to "You are helpful"),
            mapOf<String, Any?>("role" to "user", "content" to "Hello")
        )
        val hash1 = ContextHasher.hashContentBlocks(blocks)
        val hash2 = ContextHasher.hashContentBlocks(blocks)
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
        assertTrue(hash1.matches(Regex("^[a-f0-9]{64}$")))
    }

    @Test
    fun `hashContentBlocks produces different hashes for different content`() {
        val blocks1 = listOf(mapOf<String, Any?>("role" to "system", "content" to "A"))
        val blocks2 = listOf(mapOf<String, Any?>("role" to "system", "content" to "B"))
        val hash1 = ContextHasher.hashContentBlocks(blocks1)
        val hash2 = ContextHasher.hashContentBlocks(blocks2)
        assertTrue(hash1 != hash2)
    }
}
