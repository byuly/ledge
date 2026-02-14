package io.ledge.memory.domain

import io.ledge.TestFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals

class ContentHashTest {

    @Test
    fun `valid SHA-256 hex accepted`() {
        val hash = ContentHash(TestFixtures.VALID_SHA256)
        assertEquals(TestFixtures.VALID_SHA256, hash.value)
    }

    @Test
    fun `empty string rejected`() {
        assertThrows<IllegalArgumentException> { ContentHash("") }
    }

    @Test
    fun `invalid format rejected`() {
        assertThrows<IllegalArgumentException> { ContentHash("not-a-hash") }
    }
}
