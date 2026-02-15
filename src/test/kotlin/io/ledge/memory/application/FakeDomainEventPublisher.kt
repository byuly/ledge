package io.ledge.memory.application

import io.ledge.memory.application.port.DomainEventPublisher
import io.ledge.shared.DomainEvent

class FakeDomainEventPublisher : DomainEventPublisher {

    val publishedEvents = mutableListOf<DomainEvent>()

    override fun publish(event: DomainEvent) {
        publishedEvents.add(event)
    }
}
