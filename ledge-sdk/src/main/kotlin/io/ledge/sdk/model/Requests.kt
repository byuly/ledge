package io.ledge.sdk.model

data class IngestEventRequest(
    val sessionId: String,
    val agentId: String,
    val eventType: String,
    val occurredAt: String,
    val payload: Map<String, Any?>,
    val contextHash: String? = null,
    val parentEventId: String? = null,
    val schemaVersion: Int = 1
)

data class IngestBatchRequest(
    val events: List<IngestEventRequest>
)

data class CreateSessionRequest(
    val agentId: String
)

data class UpdateSessionRequest(
    val status: String
)
