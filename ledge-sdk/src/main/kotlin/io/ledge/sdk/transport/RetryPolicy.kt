package io.ledge.sdk.transport

import java.util.concurrent.ThreadLocalRandom

class RetryPolicy(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 100
) {

    fun <T> execute(action: () -> T): T {
        var lastException: LedgeApiException? = null
        for (attempt in 0..maxRetries) {
            try {
                return action()
            } catch (e: RetryableException) {
                lastException = e
                if (attempt < maxRetries) {
                    val delay = computeDelay(attempt, e.retryAfterMs)
                    Thread.sleep(delay)
                }
            } catch (e: LedgeApiException) {
                throw e
            }
        }
        throw lastException!!
    }

    internal fun computeDelay(attempt: Int, retryAfterMs: Long?): Long {
        if (retryAfterMs != null && retryAfterMs > 0) return retryAfterMs
        val exponential = baseDelayMs * (1L shl attempt)
        val jitter = ThreadLocalRandom.current().nextLong(0, exponential / 2 + 1)
        return exponential + jitter
    }
}
