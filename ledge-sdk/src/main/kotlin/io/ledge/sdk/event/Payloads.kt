package io.ledge.sdk.event

data class ContentBlock(
    val role: String,
    val content: String,
    val tokenCount: Int? = null
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
