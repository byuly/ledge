package io.ledge.ingestion.application

import io.ledge.shared.EventId

data class IngestEventResult(val eventId: EventId, val sequenceNumber: Long)
