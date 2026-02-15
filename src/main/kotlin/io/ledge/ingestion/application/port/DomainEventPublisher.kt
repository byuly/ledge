package io.ledge.ingestion.application.port

import io.ledge.shared.DomainEvent

interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}
