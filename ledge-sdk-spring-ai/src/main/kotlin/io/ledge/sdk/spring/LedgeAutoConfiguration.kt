package io.ledge.sdk.spring

import io.ledge.sdk.LedgeClient
import io.ledge.sdk.LedgeConfig
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(name = ["org.springframework.ai.chat.client.ChatClient"])
@ConditionalOnProperty(prefix = "ledge", name = ["api-key"])
@EnableConfigurationProperties(LedgeSpringAiProperties::class)
class LedgeAutoConfiguration(
    private val properties: LedgeSpringAiProperties
) {

    private var ledgeClient: LedgeClient? = null

    @Bean
    @ConditionalOnMissingBean
    fun ledgeClient(): LedgeClient {
        val config = LedgeConfig(
            baseUrl = properties.baseUrl,
            apiKey = properties.apiKey,
            batchSize = properties.batchSize,
            flushIntervalMs = properties.flushIntervalMs,
            batchingEnabled = properties.batchingEnabled,
            maxRetries = properties.maxRetries
        )
        return LedgeClient(config).also { ledgeClient = it }
    }

    @Bean
    @ConditionalOnMissingBean
    fun ledgeObservationAdvisor(ledgeClient: LedgeClient): LedgeObservationAdvisor {
        return LedgeObservationAdvisor(ledgeClient, properties.agentId)
    }

    @PreDestroy
    fun destroy() {
        ledgeClient?.close()
    }
}
