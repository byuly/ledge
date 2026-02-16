package io.ledge.infrastructure

import io.ledge.shared.AgentId
import io.ledge.shared.SessionId
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisContextCache(
    private val redis: ReactiveStringRedisTemplate
) {

    fun putSessionContext(sessionId: SessionId, payload: String) {
        redis.opsForValue()
            .set("session:${sessionId.value}:context", payload, TTL)
            .block()
    }

    fun getSessionContext(sessionId: SessionId): String? {
        return redis.opsForValue()
            .get("session:${sessionId.value}:context")
            .block()
    }

    fun putAgentLatestContext(agentId: AgentId, payload: String) {
        redis.opsForValue()
            .set("agent:${agentId.value}:context:latest", payload, TTL)
            .block()
    }

    fun getAgentLatestContext(agentId: AgentId): String? {
        return redis.opsForValue()
            .get("agent:${agentId.value}:context:latest")
            .block()
    }

    fun putSessionStatus(sessionId: SessionId, status: String) {
        redis.opsForValue()
            .set("session:${sessionId.value}:status", status, TTL)
            .block()
    }

    fun getSessionStatus(sessionId: SessionId): String? {
        return redis.opsForValue()
            .get("session:${sessionId.value}:status")
            .block()
    }

    companion object {
        private val TTL = Duration.ofHours(24)
    }
}
