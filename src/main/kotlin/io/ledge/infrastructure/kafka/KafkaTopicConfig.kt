package io.ledge.infrastructure.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KafkaTopicConfig {

    @Bean
    fun ledgeEventsTopic(): NewTopic =
        NewTopic(EVENTS_TOPIC, 6, 1.toShort())

    @Bean
    fun ledgeDlqTopic(): NewTopic =
        NewTopic(DLQ_TOPIC, 6, 1.toShort())
            .configs(mapOf("retention.ms" to (30L * 24 * 60 * 60 * 1000).toString()))

    companion object {
        const val EVENTS_TOPIC = "ledge.events"
        const val DLQ_TOPIC = "ledge.dlq"
    }
}
