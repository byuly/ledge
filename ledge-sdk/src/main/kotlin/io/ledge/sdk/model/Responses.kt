package io.ledge.sdk.model

data class IngestEventResponse(
    val eventId: String,
    val sequenceNumber: Long
)

data class IngestBatchResponse(
    val accepted: Int,
    val results: List<IngestEventResponse>
)

data class SessionResponse(
    val sessionId: String,
    val agentId: String,
    val tenantId: String,
    val startedAt: String,
    val endedAt: String?,
    val status: String,
    val nextSequenceNumber: Long
)
