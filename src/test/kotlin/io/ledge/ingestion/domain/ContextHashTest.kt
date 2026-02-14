package io.ledge.ingestion.domain

import io.ledge.TestFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals

class ContextHashTest {

    @Test
    fun `valid 64-char lowercase hex string accepted`() {
        val hash = ContextHash(TestFixtures.VALID_SHA256)
        assertEquals(TestFixtures.VALID_SHA256, hash.value)
    }

    @Test
    fun `empty string rejected`() {
        assertThrows<IllegalArgumentException> { ContextHash("") }
    }

    @Test
    fun `uppercase hex rejected`() {
        // SHA-256 regex requires lowercase a-f only
        assertThrows<IllegalArgumentException> {
            ContextHash(TestFixtures.VALID_SHA256.uppercase())
        }
    }

    @Test
    fun `wrong length rejected`() {
        assertThrows<IllegalArgumentException> { ContextHash("a1b2c3") }
    }

    @Test
    fun `non-hex characters rejected`() {
        // Replace first char with 'g' (outside hex range a-f) while keeping correct length
        assertThrows<IllegalArgumentException> {
            ContextHash("g" + TestFixtures.VALID_SHA256.drop(1))
        }
    }
}
