package io.ledge.sdk.event

import java.time.Instant

data class ObservationEvent(
    val sessionId: String,
    val agentId: String,
    val eventType: EventType,
    val occurredAt: Instant = Instant.now(),
    val payload: Map<String, Any?> = emptyMap(),
    val contextHash: String? = null,
    val parentEventId: String? = null,
    val schemaVersion: Int = 1
)
