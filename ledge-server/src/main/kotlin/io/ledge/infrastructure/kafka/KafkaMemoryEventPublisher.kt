package io.ledge.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.ingestion.application.port.MemoryEventPublisher
import io.ledge.ingestion.domain.MemoryEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaMemoryEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : MemoryEventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: MemoryEvent) {
        val envelope = MemoryEventEnvelope.from(event)
        val json = objectMapper.writeValueAsString(envelope)
        val key = event.tenantId.value.toString()

        kafkaTemplate.send(KafkaTopicConfig.EVENTS_TOPIC, key, json).get()
        log.debug("Published event {} to {}", event.id, KafkaTopicConfig.EVENTS_TOPIC)
    }

    override fun publishAll(events: List<MemoryEvent>) {
        events.forEach { publish(it) }
    }
}
