package io.ledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.clickhouse.ClickHouseContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.sql.DriverManager
import java.time.Duration
import java.util.Properties

@Testcontainers
@Tag("smoke")
class InfrastructureSmokeTest {

    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

    @Container
    val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))

    @Container
    val redis = GenericContainer<Nothing>("redis:7-alpine").withExposedPorts(6379)

    @Container
    val clickhouse = ClickHouseContainer("clickhouse/clickhouse-server:24.3")

    @Test
    fun `postgres is reachable`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT 1")
                rs.next()
                assertEquals(1, rs.getInt(1))
            }
        }
    }

    @Test
    fun `kafka is reachable`() {
        val bootstrapServers = kafka.bootstrapServers
        val topic = "smoke-test"

        val adminProps = Properties().apply {
            put("bootstrap.servers", bootstrapServers)
        }
        AdminClient.create(adminProps).use { admin ->
            admin.createTopics(listOf(NewTopic(topic, 1, 1))).all().get()
        }

        val producerProps = Properties().apply {
            put("bootstrap.servers", bootstrapServers)
            put("key.serializer", StringSerializer::class.java.name)
            put("value.serializer", StringSerializer::class.java.name)
        }
        KafkaProducer<String, String>(producerProps).use { producer ->
            producer.send(ProducerRecord(topic, "key", "hello")).get()
        }

        val consumerProps = Properties().apply {
            put("bootstrap.servers", bootstrapServers)
            put("group.id", "smoke-consumer")
            put("key.deserializer", StringDeserializer::class.java.name)
            put("value.deserializer", StringDeserializer::class.java.name)
            put("auto.offset.reset", "earliest")
        }
        KafkaConsumer<String, String>(consumerProps).use { consumer ->
            consumer.subscribe(listOf(topic))
            val records = consumer.poll(Duration.ofSeconds(10))
            assertEquals(1, records.count())
            assertEquals("hello", records.first().value())
        }
    }

    @Test
    fun `redis is reachable`() {
        val socket = java.net.Socket(redis.host, redis.getMappedPort(6379))
        socket.use {
            val out = it.getOutputStream()
            val input = it.getInputStream()
            out.write("PING\r\n".toByteArray())
            out.flush()
            val response = input.readNBytes(7).toString(Charsets.UTF_8)
            assertEquals("+PONG\r\n", response)
        }
    }

    @Test
    fun `clickhouse is reachable`() {
        DriverManager.getConnection(clickhouse.jdbcUrl, clickhouse.username, clickhouse.password).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT 1")
                rs.next()
                assertEquals(1, rs.getInt(1))
            }
        }
    }
}
