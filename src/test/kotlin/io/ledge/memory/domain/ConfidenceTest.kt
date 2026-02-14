package io.ledge.memory.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals

class ConfidenceTest {

    @Test
    fun `0_0 accepted as boundary`() {
        assertEquals(0.0f, Confidence(0.0f).value)
    }

    @Test
    fun `1_0 accepted as boundary`() {
        assertEquals(1.0f, Confidence(1.0f).value)
    }

    @Test
    fun `0_5 accepted as mid-range`() {
        assertEquals(0.5f, Confidence(0.5f).value)
    }

    @Test
    fun `negative value rejected`() {
        assertThrows<IllegalArgumentException> { Confidence(-0.1f) }
    }

    @Test
    fun `value above 1_0 rejected`() {
        assertThrows<IllegalArgumentException> { Confidence(1.1f) }
    }
}
