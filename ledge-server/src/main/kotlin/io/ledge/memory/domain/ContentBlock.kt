package io.ledge.memory.domain

data class ContentBlock(
    val blockType: String,
    val content: String,
    val tokenCount: Int,
    val source: String?
)
