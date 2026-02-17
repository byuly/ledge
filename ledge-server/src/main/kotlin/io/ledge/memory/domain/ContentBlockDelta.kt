package io.ledge.memory.domain

data class ContentBlockDelta(
    val blockType: String,
    val before: ContentBlock,
    val after: ContentBlock
)
