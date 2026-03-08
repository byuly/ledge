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
import kotlinx.coroutines.future.await
import java.io.Closeable

class LedgeAsyncClient(
    private val config: LedgeConfig,
    private val transport: HttpTransport = JdkHttpTransport(config)
) : Closeable {

    private val batcher: EventBatcher? = if (config.batchingEnabled) {
        EventBatcher(transport, config.batchSize, config.flushIntervalMs)
    } else null

    suspend fun createSession(agentId: String): LedgeSession {
        val response = transport.postAsync(
            "/api/v1/sessions",
            CreateSessionRequest(agentId = agentId),
            SessionResponse::class.java
        ).await()
        return LedgeSession(
            sessionId = response.sessionId,
            agentId = agentId,
            client = LedgeClient(config, transport)
        )
    }

    suspend fun completeSession(sessionId: String) {
        transport.patchNoContentAsync(
            "/api/v1/sessions/$sessionId",
            UpdateSessionRequest(status = "COMPLETED")
        ).await()
    }

    suspend fun abandonSession(sessionId: String) {
        transport.patchNoContentAsync(
            "/api/v1/sessions/$sessionId",
            UpdateSessionRequest(status = "ABANDONED")
        ).await()
    }

    suspend fun emit(event: ObservationEvent): String {
        val request = IngestEventRequest(
            sessionId = event.sessionId,
            agentId = event.agentId,
            eventType = event.eventType.name,
            occurredAt = event.occurredAt.toString(),
            payload = event.payload,
            contextHash = event.contextHash,
            parentEventId = event.parentEventId,
            schemaVersion = event.schemaVersion
        )
        if (batcher != null) {
            batcher.enqueue(request)
            return "pending-${System.nanoTime()}"
        }
        val response = transport.postAsync(
            "/api/v1/events",
            request,
            IngestEventResponse::class.java
        ).await()
        return response.eventId
    }

    fun flush() {
        batcher?.flush()
    }

    override fun close() {
        batcher?.close()
    }
}
