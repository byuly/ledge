package io.ledge.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.infrastructure.RedisContextCache
import io.ledge.ingestion.domain.EventType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RedisWriterConsumer(
    private val redisContextCache: RedisContextCache,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaTopicConfig.EVENTS_TOPIC],
        groupId = "postgres-redis-writer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>) {
        val envelope = objectMapper.readValue(record.value(), MemoryEventEnvelope::class.java)
        val event = envelope.toMemoryEvent()

        if (event.eventType == EventType.CONTEXT_ASSEMBLED) {
            try {
                redisContextCache.putSessionContext(event.sessionId, event.payload)
                redisContextCache.putAgentLatestContext(event.agentId, event.payload)
                log.debug("Redis writer cached context for session {} agent {}", event.sessionId, event.agentId)
            } catch (e: Exception) {
                log.warn("Failed to write context to Redis for event {}: {}", envelope.eventId, e.message)
            }
        }
    }
}
