package io.ledge.ingestion.application

import io.ledge.ingestion.application.port.DomainEventPublisher
import io.ledge.shared.DomainEvent

class FakeDomainEventPublisher : DomainEventPublisher {

    val publishedEvents = mutableListOf<DomainEvent>()

    override fun publish(event: DomainEvent) {
        publishedEvents.add(event)
    }
}
