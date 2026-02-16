package io.ledge.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ledge.TestFixtures
import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.domain.SchemaVersion
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Instant
import java.util.concurrent.CompletableFuture

class KafkaMemoryEventPublisherTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val kafkaTemplate: KafkaTemplate<String, String> = mock()
    private val publisher = KafkaMemoryEventPublisher(kafkaTemplate, objectMapper)

    private fun memoryEvent(
        eventType: EventType = EventType.USER_INPUT,
        contextHash: ContextHash? = null,
        parentEventId: io.ledge.shared.EventId? = null
    ): MemoryEvent {
        val tenantId = TestFixtures.tenantId()
        return MemoryEvent(
            id = TestFixtures.eventId(),
            sessionId = TestFixtures.sessionId(),
            agentId = TestFixtures.agentId(),
            tenantId = tenantId,
            eventType = eventType,
            sequenceNumber = 1L,
            occurredAt = Instant.parse("2025-01-15T10:30:00Z"),
            payload = """{"text":"hello"}""",
            contextHash = contextHash,
            parentEventId = parentEventId,
            schemaVersion = SchemaVersion(1)
        )
    }

    @Test
    fun `envelope round-trip preserves all fields`() {
        val event = memoryEvent(
            eventType = EventType.CONTEXT_ASSEMBLED,
            contextHash = TestFixtures.contextHash(),
            parentEventId = TestFixtures.eventId()
        )

        val envelope = MemoryEventEnvelope.from(event)
        val json = objectMapper.writeValueAsString(envelope)
        val deserialized = objectMapper.readValue(json, MemoryEventEnvelope::class.java)
        val restored = deserialized.toMemoryEvent()

        assertEquals(event, restored)
    }

    @Test
    fun `envelope round-trip preserves null contextHash and parentEventId`() {
        val event = memoryEvent(contextHash = null, parentEventId = null)

        val envelope = MemoryEventEnvelope.from(event)
        val json = objectMapper.writeValueAsString(envelope)
        val deserialized = objectMapper.readValue(json, MemoryEventEnvelope::class.java)

        assertNull(deserialized.contextHash)
        assertNull(deserialized.parentEventId)

        val restored = deserialized.toMemoryEvent()
        assertEquals(event, restored)
    }

    @Test
    fun `publish uses tenantId as Kafka record key`() {
        val event = memoryEvent()
        val expectedKey = event.tenantId.value.toString()

        val sendResult = SendResult<String, String>(
            ProducerRecord(KafkaTopicConfig.EVENTS_TOPIC, expectedKey, "{}"),
            RecordMetadata(TopicPartition(KafkaTopicConfig.EVENTS_TOPIC, 0), 0, 0, 0, 0, 0)
        )
        val future = CompletableFuture.completedFuture(sendResult)

        val captor = argumentCaptor<String>()
        whenever(kafkaTemplate.send(
            captor.capture(), captor.capture(), captor.capture()
        )).thenReturn(future)

        publisher.publish(event)

        val capturedValues = captor.allValues
        assertEquals(KafkaTopicConfig.EVENTS_TOPIC, capturedValues[0])
        assertEquals(expectedKey, capturedValues[1])
    }

    @Test
    fun `publishAll publishes each event individually`() {
        val event1 = memoryEvent()
        val event2 = memoryEvent(eventType = EventType.AGENT_OUTPUT)

        val sendResult = SendResult<String, String>(
            ProducerRecord(KafkaTopicConfig.EVENTS_TOPIC, "", "{}"),
            RecordMetadata(TopicPartition(KafkaTopicConfig.EVENTS_TOPIC, 0), 0, 0, 0, 0, 0)
        )
        val future = CompletableFuture.completedFuture(sendResult)

        whenever(kafkaTemplate.send(
            org.mockito.kotlin.any<String>(),
            org.mockito.kotlin.any<String>(),
            org.mockito.kotlin.any<String>()
        )).thenReturn(future)

        publisher.publishAll(listOf(event1, event2))

        org.mockito.kotlin.verify(kafkaTemplate, org.mockito.kotlin.times(2)).send(
            org.mockito.kotlin.any<String>(),
            org.mockito.kotlin.any<String>(),
            org.mockito.kotlin.any<String>()
        )
    }
}
