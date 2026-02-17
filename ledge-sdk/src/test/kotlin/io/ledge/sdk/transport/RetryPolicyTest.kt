package io.ledge.sdk.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RetryPolicyTest {

    @Test
    fun `succeeds on first attempt`() {
        val policy = RetryPolicy(maxRetries = 3)
        var attempts = 0
        val result = policy.execute {
            attempts++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retries on RetryableException and eventually succeeds`() {
        val policy = RetryPolicy(maxRetries = 3, baseDelayMs = 1)
        var attempts = 0
        val result = policy.execute {
            attempts++
            if (attempts < 3) throw RetryableException(503, "Service Unavailable")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `throws after max retries exhausted`() {
        val policy = RetryPolicy(maxRetries = 2, baseDelayMs = 1)
        var attempts = 0
        assertThrows<RetryableException> {
            policy.execute {
                attempts++
                throw RetryableException(503, "Service Unavailable")
            }
        }
        assertEquals(3, attempts) // initial + 2 retries
    }

    @Test
    fun `does not retry non-retryable LedgeApiException`() {
        val policy = RetryPolicy(maxRetries = 3, baseDelayMs = 1)
        var attempts = 0
        assertThrows<LedgeApiException> {
            policy.execute {
                attempts++
                throw LedgeApiException(400, "Bad Request")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `exponential delay increases with attempt`() {
        val policy = RetryPolicy(baseDelayMs = 100)
        val delay0 = policy.computeDelay(0, null)
        val delay1 = policy.computeDelay(1, null)
        val delay2 = policy.computeDelay(2, null)
        // delay0 should be in [100, 150], delay1 in [200, 300], delay2 in [400, 600]
        assertTrue(delay0 in 100..150, "delay0=$delay0")
        assertTrue(delay1 in 200..300, "delay1=$delay1")
        assertTrue(delay2 in 400..600, "delay2=$delay2")
    }

    @Test
    fun `respects retryAfterMs when provided`() {
        val policy = RetryPolicy(baseDelayMs = 100)
        val delay = policy.computeDelay(0, 5000)
        assertEquals(5000, delay)
    }
}
