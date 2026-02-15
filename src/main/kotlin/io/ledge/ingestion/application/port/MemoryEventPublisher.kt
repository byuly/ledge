package io.ledge.ingestion.application.port

import io.ledge.ingestion.domain.MemoryEvent

interface MemoryEventPublisher {
    fun publish(event: MemoryEvent)
    fun publishAll(events: List<MemoryEvent>)
}
