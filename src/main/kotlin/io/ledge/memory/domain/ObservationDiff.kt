package io.ledge.memory.domain

import io.ledge.shared.EventId

data class ObservationDiff(
    val fromEventId: EventId,
    val toEventId: EventId,
    val addedBlocks: List<ContentBlock>,
    val removedBlocks: List<ContentBlock>,
    val modifiedBlocks: List<ContentBlockDelta>
)
