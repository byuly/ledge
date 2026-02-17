package io.ledge.shared

import java.time.Instant

sealed interface DomainEvent {
    val occurredAt: Instant

    data class SessionCompleted(
        val sessionId: SessionId,
        val agentId: AgentId,
        val tenantId: TenantId,
        override val occurredAt: Instant = Instant.now()
    ) : DomainEvent

    data class TenantPurged(
        val tenantId: TenantId,
        override val occurredAt: Instant = Instant.now()
    ) : DomainEvent
}
