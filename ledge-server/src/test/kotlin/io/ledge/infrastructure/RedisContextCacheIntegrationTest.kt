package io.ledge.infrastructure

import io.ledge.TestFixtures
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

@Tag("integration")
class RedisContextCacheIntegrationTest {

    private val connectionFactory = LettuceConnectionFactory(
        TestContainers.redisHost(),
        TestContainers.redisPort()
    ).also { it.afterPropertiesSet() }

    private val redisTemplate = ReactiveStringRedisTemplate(connectionFactory)
    private val cache = RedisContextCache(redisTemplate, SimpleMeterRegistry())

    @BeforeEach
    fun flushAll() {
        connectionFactory.connection.serverCommands().flushAll()
    }

    @Test
    fun `put and get session context`() {
        val sessionId = TestFixtures.sessionId()
        val payload = """{"blocks":[{"type":"system","content":"hello"}]}"""

        cache.putSessionContext(sessionId, payload)
        val result = cache.getSessionContext(sessionId)

        assertEquals(payload, result)
    }

    @Test
    fun `get session context returns null for missing key`() {
        val result = cache.getSessionContext(TestFixtures.sessionId())
        assertNull(result)
    }

    @Test
    fun `put and get agent latest context`() {
        val agentId = TestFixtures.agentId()
        val payload = """{"blocks":[]}"""

        cache.putAgentLatestContext(agentId, payload)
        val result = cache.getAgentLatestContext(agentId)

        assertEquals(payload, result)
    }

    @Test
    fun `get agent latest context returns null for missing key`() {
        val result = cache.getAgentLatestContext(TestFixtures.agentId())
        assertNull(result)
    }

    @Test
    fun `put and get session status`() {
        val sessionId = TestFixtures.sessionId()

        cache.putSessionStatus(sessionId, "ACTIVE")
        assertEquals("ACTIVE", cache.getSessionStatus(sessionId))

        cache.putSessionStatus(sessionId, "COMPLETED")
        assertEquals("COMPLETED", cache.getSessionStatus(sessionId))
    }

    @Test
    fun `get session status returns null for missing key`() {
        val result = cache.getSessionStatus(TestFixtures.sessionId())
        assertNull(result)
    }
}
