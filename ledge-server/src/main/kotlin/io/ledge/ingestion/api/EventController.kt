package io.ledge.ingestion.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.ingestion.application.IngestionService
import io.ledge.shared.TenantId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/events")
class EventController(
    private val ingestionService: IngestionService,
    private val objectMapper: ObjectMapper
) {

    @PostMapping
    suspend fun ingestEvent(
        @RequestHeader("X-Tenant-Id") tenantId: String,
        @RequestBody request: IngestEventRequest
    ): ResponseEntity<IngestEventResponse> = withContext(Dispatchers.IO) {
        val command = request.toCommand(objectMapper)
        val result = ingestionService.ingestEvent(TenantId.fromString(tenantId), command)
        ResponseEntity.accepted().body(
            IngestEventResponse(
                eventId = result.eventId.value.toString(),
                sequenceNumber = result.sequenceNumber
            )
        )
    }

    @PostMapping("/batch")
    suspend fun ingestBatch(
        @RequestHeader("X-Tenant-Id") tenantId: String,
        @RequestBody request: IngestBatchRequest
    ): ResponseEntity<IngestBatchResponse> = withContext(Dispatchers.IO) {
        val commands = request.events.map { it.toCommand(objectMapper) }
        val results = ingestionService.ingestBatch(TenantId.fromString(tenantId), commands)
        ResponseEntity.accepted().body(
            IngestBatchResponse(
                accepted = results.size,
                results = results.map {
                    IngestEventResponse(
                        eventId = it.eventId.value.toString(),
                        sequenceNumber = it.sequenceNumber
                    )
                }
            )
        )
    }
}
