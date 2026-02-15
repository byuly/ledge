package io.ledge.ingestion.application

import io.ledge.ingestion.application.port.MemoryEventPublisher
import io.ledge.ingestion.domain.MemoryEvent

class FakeMemoryEventPublisher : MemoryEventPublisher {

    val publishedEvents = mutableListOf<MemoryEvent>()

    override fun publish(event: MemoryEvent) {
        publishedEvents.add(event)
    }

    override fun publishAll(events: List<MemoryEvent>) {
        publishedEvents.addAll(events)
    }
}
