package io.ledge.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.infrastructure.RedisContextCache
import io.ledge.ingestion.application.port.SessionRepository
import io.ledge.ingestion.domain.EventType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RedisWriterConsumer(
    private val redisContextCache: RedisContextCache,
    private val sessionRepository: SessionRepository,
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

        when (event.eventType) {
            EventType.CONTEXT_ASSEMBLED -> {
                try {
                    redisContextCache.putSessionContext(event.sessionId, event.payload)
                    redisContextCache.putAgentLatestContext(event.agentId, event.payload)
                    log.debug("Redis writer cached context for session {} agent {}", event.sessionId, event.agentId)
                } catch (e: Exception) {
                    log.warn("Failed to write context to Redis for event {}: {}", envelope.eventId, e.message)
                }
            }
            EventType.SESSION_COMPLETED -> {
                try {
                    val session = sessionRepository.findById(event.sessionId, event.tenantId)
                    if (session != null) {
                        session.complete()
                        sessionRepository.save(session)
                        log.debug("Consumer completed session {}", event.sessionId)
                    } else {
                        log.warn("Session not found for SESSION_COMPLETED event: {}", event.sessionId)
                    }
                } catch (e: Exception) {
                    log.warn("Failed to complete session {} for event {}: {}", event.sessionId, envelope.eventId, e.message)
                }
            }
            EventType.SESSION_ABANDONED -> {
                try {
                    val session = sessionRepository.findById(event.sessionId, event.tenantId)
                    if (session != null) {
                        session.abandon()
                        sessionRepository.save(session)
                        log.debug("Consumer abandoned session {}", event.sessionId)
                    } else {
                        log.warn("Session not found for SESSION_ABANDONED event: {}", event.sessionId)
                    }
                } catch (e: Exception) {
                    log.warn("Failed to abandon session {} for event {}: {}", event.sessionId, envelope.eventId, e.message)
                }
            }
            else -> { /* no-op for other event types in this consumer group */ }
        }
    }
}
