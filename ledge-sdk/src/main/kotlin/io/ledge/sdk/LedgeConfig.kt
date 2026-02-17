package io.ledge.sdk

data class LedgeConfig(
    val baseUrl: String,
    val apiKey: String,
    val batchSize: Int = 50,
    val flushIntervalMs: Long = 100,
    val batchingEnabled: Boolean = true,
    val maxRetries: Int = 3,
    val connectTimeoutMs: Long = 5000,
    val requestTimeoutMs: Long = 10000
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(batchSize in 1..500) { "batchSize must be between 1 and 500" }
        require(flushIntervalMs > 0) { "flushIntervalMs must be positive" }
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
    }
}
