package io.ledge.infrastructure.kafka

import io.ledge.shared.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoggingDomainEventPublisher :
    io.ledge.ingestion.application.port.DomainEventPublisher,
    io.ledge.tenant.application.port.DomainEventPublisher,
    io.ledge.memory.application.port.DomainEventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: DomainEvent) {
        log.info("Domain event published: {}", event)
    }
}
