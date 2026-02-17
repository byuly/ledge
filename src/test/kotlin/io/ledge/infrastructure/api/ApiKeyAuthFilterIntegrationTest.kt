package io.ledge.infrastructure.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.TestFixtures
import io.ledge.infrastructure.TestContainers
import io.ledge.tenant.application.FakeTenantRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.MessageDigest

@Tag("integration")
class ApiKeyAuthFilterIntegrationTest {

    private val connectionFactory = LettuceConnectionFactory(
        TestContainers.redisHost(),
        TestContainers.redisPort()
    ).also { it.afterPropertiesSet() }

    private val redisTemplate = ReactiveStringRedisTemplate(connectionFactory)
    private val tenantRepo = FakeTenantRepository()
    private val rateLimit = 3L

    private val filter = ApiKeyAuthFilter(
        tenantRepository = tenantRepo,
        redisTemplate = redisTemplate,
        rateLimit = rateLimit,
        objectMapper = ObjectMapper()
    )

    private val passThroughChain = WebFilterChain { Mono.empty() }

    @BeforeEach
    fun flushRedis() {
        connectionFactory.connection.serverCommands().flushAll()
    }

    @Test
    fun `rate limit returns 429 after threshold exceeded`() {
        val rawKey = "rate-limit-test-key"
        val tenant = TestFixtures.tenant(apiKeyHash = sha256(rawKey))
        tenantRepo.save(tenant)

        // First rateLimit requests should all pass
        repeat(rateLimit.toInt()) {
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/events")
                    .header("X-API-Key", rawKey)
                    .build()
            )
            filter.filter(exchange, passThroughChain).block()
            assertNotEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                exchange.response.statusCode,
                "Request ${it + 1} should be within rate limit"
            )
        }

        // rateLimit+1 request should be rejected
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/events")
                .header("X-API-Key", rawKey)
                .build()
        )
        filter.filter(exchange, passThroughChain).block()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
    }

    @Test
    fun `rate limit resets after window`() {
        val rawKey = "rate-limit-reset-key"
        val tenant = TestFixtures.tenant(apiKeyHash = sha256(rawKey))
        tenantRepo.save(tenant)

        // Exhaust the rate limit
        repeat(rateLimit.toInt() + 1) {
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/events")
                    .header("X-API-Key", rawKey)
                    .build()
            )
            filter.filter(exchange, passThroughChain).block()
        }

        // Simulate window expiry by deleting the Redis key
        val rateLimitKey = "tenant:${tenant.id.value}:ratelimit"
        redisTemplate.delete(rateLimitKey).block()

        // Next request should succeed (window reset)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/events")
                .header("X-API-Key", rawKey)
                .build()
        )
        filter.filter(exchange, passThroughChain).block()

        assertNotEquals(
            HttpStatus.TOO_MANY_REQUESTS,
            exchange.response.statusCode,
            "Request should succeed after rate limit window reset"
        )
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
