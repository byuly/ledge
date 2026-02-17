package io.ledge.sdk.spring

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ledge")
data class LedgeSpringAiProperties(
    val baseUrl: String = "http://localhost:8080",
    val apiKey: String = "",
    val agentId: String = "",
    val batchSize: Int = 50,
    val flushIntervalMs: Long = 100,
    val batchingEnabled: Boolean = true,
    val maxRetries: Int = 3
)
