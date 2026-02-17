package io.ledge.sdk

import io.ledge.sdk.event.ObservationEvent
import io.ledge.sdk.model.CreateSessionRequest
import io.ledge.sdk.model.IngestEventRequest
import io.ledge.sdk.model.IngestEventResponse
import io.ledge.sdk.model.SessionResponse
import io.ledge.sdk.model.UpdateSessionRequest
import io.ledge.sdk.transport.EventBatcher
import io.ledge.sdk.transport.HttpTransport
import io.ledge.sdk.transport.JdkHttpTransport
import java.io.Closeable

class LedgeClient(
    private val config: LedgeConfig,
    private val transport: HttpTransport = JdkHttpTransport(config)
) : Closeable {

    private val batcher: EventBatcher? = if (config.batchingEnabled) {
        EventBatcher(transport, config.batchSize, config.flushIntervalMs)
    } else null

    fun createSession(agentId: String): LedgeSession {
        val response = transport.post(
            "/api/v1/sessions",
            CreateSessionRequest(agentId = agentId),
            SessionResponse::class.java
        )
        return LedgeSession(
            sessionId = response.sessionId,
            agentId = agentId,
            client = this
        )
    }

    fun completeSession(sessionId: String): SessionResponse {
        return transport.patch(
            "/api/v1/sessions/$sessionId",
            UpdateSessionRequest(status = "COMPLETED"),
            SessionResponse::class.java
        )
    }

    fun abandonSession(sessionId: String): SessionResponse {
        return transport.patch(
            "/api/v1/sessions/$sessionId",
            UpdateSessionRequest(status = "ABANDONED"),
            SessionResponse::class.java
        )
    }

    fun emit(event: ObservationEvent): String {
        val request = toIngestRequest(event)
        if (batcher != null) {
            batcher.enqueue(request)
            // Return a placeholder event ID for batched events
            return "pending-${System.nanoTime()}"
        }
        val response = transport.post(
            "/api/v1/events",
            request,
            IngestEventResponse::class.java
        )
        return response.eventId
    }

    fun flush() {
        batcher?.flush()
    }

    override fun close() {
        batcher?.close()
    }

    private fun toIngestRequest(event: ObservationEvent): IngestEventRequest {
        return IngestEventRequest(
            sessionId = event.sessionId,
            agentId = event.agentId,
            eventType = event.eventType.name,
            occurredAt = event.occurredAt.toString(),
            payload = event.payload,
            contextHash = event.contextHash,
            parentEventId = event.parentEventId,
            schemaVersion = event.schemaVersion
        )
    }
}
