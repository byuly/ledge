package io.ledge.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ledge.TestFixtures
import io.ledge.infrastructure.RedisContextCache
import io.ledge.infrastructure.TestContainers
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.ingestion.application.FakeSessionRepository
import io.ledge.ingestion.infrastructure.ClickHouseMemoryEventWriter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger

@Tag("integration")
class KafkaIntegrationTest {

    companion object {
        private val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
        private val groupCounter = AtomicInteger(0)

        private lateinit var objectMapper: ObjectMapper
        private lateinit var kafkaTemplate: KafkaTemplate<String, String>
        private lateinit var publisher: KafkaMemoryEventPublisher
        private lateinit var clickHouseWriter: ClickHouseMemoryEventWriter
        private lateinit var redisContextCache: RedisContextCache
        private lateinit var clickHouseConsumer: ClickHouseWriterConsumer
        private lateinit var redisConsumer: RedisWriterConsumer

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            kafka.start()

            val adminProps = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers)
            AdminClient.create(adminProps).use { admin ->
                admin.createTopics(
                    listOf(
                        NewTopic(KafkaTopicConfig.EVENTS_TOPIC, 1, 1.toShort()),
                        NewTopic(KafkaTopicConfig.DLQ_TOPIC, 1, 1.toShort())
                    )
                ).all().get()
            }

            objectMapper = jacksonObjectMapper().apply {
                findAndRegisterModules()
            }

            val producerProps = mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java
            )
            val producerFactory = DefaultKafkaProducerFactory<String, String>(producerProps)
            kafkaTemplate = KafkaTemplate(producerFactory)
            publisher = KafkaMemoryEventPublisher(kafkaTemplate, objectMapper)

            clickHouseWriter = ClickHouseMemoryEventWriter(TestContainers.clickHouseUrl(), SimpleMeterRegistry())

            val lettuceFactory = LettuceConnectionFactory(TestContainers.redisHost(), TestContainers.redisPort())
            lettuceFactory.afterPropertiesSet()
            val redisTemplate = ReactiveStringRedisTemplate(lettuceFactory)
            redisContextCache = RedisContextCache(redisTemplate, SimpleMeterRegistry())

            clickHouseConsumer = ClickHouseWriterConsumer(clickHouseWriter, objectMapper)
            redisConsumer = RedisWriterConsumer(redisContextCache, FakeSessionRepository(), objectMapper)
        }

        private fun uniqueGroupId(prefix: String): String =
            "$prefix-${groupCounter.incrementAndGet()}"
    }

    @BeforeEach
    fun cleanClickHouse() {
        DriverManager.getConnection(TestContainers.clickHouseUrl()).use { conn ->
            conn.createStatement().use { it.execute("TRUNCATE TABLE ledge.memory_events") }
        }
    }

    private fun makeEvent(
        eventType: EventType = EventType.USER_INPUT,
        payload: String = """{"text":"hello"}"""
    ): MemoryEvent {
        return MemoryEvent(
            id = TestFixtures.eventId(),
            sessionId = TestFixtures.sessionId(),
            agentId = TestFixtures.agentId(),
            tenantId = TestFixtures.tenantId(),
            eventType = eventType,
            occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            payload = payload,
            contextHash = null,
            parentEventId = null,
            schemaVersion = SchemaVersion(1)
        )
    }

    private fun consumeAll(groupId: String): List<ConsumerRecord<String, String>> {
        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
        )
        return KafkaConsumer<String, String>(consumerProps).use { consumer ->
            consumer.subscribe(listOf(KafkaTopicConfig.EVENTS_TOPIC))
            val records = consumer.poll(Duration.ofSeconds(10))
            records.toList()
        }
    }

    private fun findRecord(
        records: List<ConsumerRecord<String, String>>,
        eventId: String
    ): ConsumerRecord<String, String> {
        return records.first { record ->
            val envelope = objectMapper.readValue(record.value(), MemoryEventEnvelope::class.java)
            envelope.eventId == eventId
        }
    }

    @Test
    fun `publish event and consume with ClickHouse writer`() {
        val event = makeEvent()
        publisher.publish(event)

        val records = consumeAll(uniqueGroupId("ch-test"))
        assertFalse(records.isEmpty())

        val record = findRecord(records, event.id.value.toString())
        assertEquals(event.tenantId.value.toString(), record.key())

        clickHouseConsumer.consume(record)

        DriverManager.getConnection(TestContainers.clickHouseUrl()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    "SELECT event_id FROM ledge.memory_events WHERE event_id = '${event.id.value}'"
                )
                assertTrue(rs.next())
                assertEquals(event.id.value.toString(), rs.getString("event_id"))
            }
        }
    }

    @Test
    fun `CONTEXT_ASSEMBLED event updates Redis cache`() {
        val event = makeEvent(
            eventType = EventType.CONTEXT_ASSEMBLED,
            payload = """{"context":"full window data"}"""
        )
        publisher.publish(event)

        val records = consumeAll(uniqueGroupId("redis-test"))
        assertFalse(records.isEmpty())

        val record = findRecord(records, event.id.value.toString())
        redisConsumer.consume(record)

        val sessionContext = redisContextCache.getSessionContext(event.sessionId)
        assertNotNull(sessionContext)
        assertEquals(event.payload, sessionContext)

        val agentContext = redisContextCache.getAgentLatestContext(event.agentId)
        assertNotNull(agentContext)
        assertEquals(event.payload, agentContext)
    }

    @Test
    fun `non-CONTEXT_ASSEMBLED event does not update Redis cache`() {
        val event = makeEvent(eventType = EventType.USER_INPUT)
        publisher.publish(event)

        val records = consumeAll(uniqueGroupId("redis-noop-test"))
        assertFalse(records.isEmpty())

        val record = findRecord(records, event.id.value.toString())
        redisConsumer.consume(record)

        assertNull(redisContextCache.getSessionContext(event.sessionId))
        assertNull(redisContextCache.getAgentLatestContext(event.agentId))
    }

    @Test
    fun `envelope serialization round-trip through Kafka`() {
        val event = makeEvent(
            eventType = EventType.CONTEXT_ASSEMBLED,
            payload = """{"blocks":[{"type":"user","content":"test"}]}"""
        )
        publisher.publish(event)

        val records = consumeAll(uniqueGroupId("roundtrip-test"))
        assertFalse(records.isEmpty())

        val record = findRecord(records, event.id.value.toString())
        val envelope = objectMapper.readValue(record.value(), MemoryEventEnvelope::class.java)
        val restored = envelope.toMemoryEvent()

        assertEquals(event, restored)
    }
}
