package io.ledge.ingestion.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals

class SchemaVersionTest {

    @Test
    fun `positive integer accepted`() {
        assertEquals(1, SchemaVersion(1).value)
        assertEquals(100, SchemaVersion(100).value)
    }

    @Test
    fun `zero rejected`() {
        assertThrows<IllegalArgumentException> { SchemaVersion(0) }
    }

    @Test
    fun `negative rejected`() {
        assertThrows<IllegalArgumentException> { SchemaVersion(-1) }
    }
}
