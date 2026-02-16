package io.ledge.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.ingestion.infrastructure.ClickHouseMemoryEventWriter
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ClickHouseWriterConsumer(
    private val writer: ClickHouseMemoryEventWriter,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaTopicConfig.EVENTS_TOPIC],
        groupId = "clickhouse-writer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), MemoryEventEnvelope::class.java)
        val event = envelope.toMemoryEvent()
        writer.write(event)
        log.debug("ClickHouse writer consumed event {}", envelope.eventId)
    }
}
