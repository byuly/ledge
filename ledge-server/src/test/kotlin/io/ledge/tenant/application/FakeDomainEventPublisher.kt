package io.ledge.tenant.application

import io.ledge.shared.DomainEvent
import io.ledge.tenant.application.port.DomainEventPublisher

class FakeDomainEventPublisher : DomainEventPublisher {

    val publishedEvents = mutableListOf<DomainEvent>()

    override fun publish(event: DomainEvent) {
        publishedEvents.add(event)
    }
}
