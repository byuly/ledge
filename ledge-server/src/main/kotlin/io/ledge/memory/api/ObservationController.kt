package io.ledge.memory.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.memory.application.MemoryService
import io.ledge.shared.AgentId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class ObservationController(
    private val memoryService: MemoryService,
    private val objectMapper: ObjectMapper
) {

    @GetMapping("/api/v1/agents/{agentId}/context")
    suspend fun getContext(
        @RequestHeader("X-Tenant-Id") tenantId: String,
        @PathVariable agentId: String,
        @RequestParam at: String
    ): ResponseEntity<ContextResponse> = withContext(Dispatchers.IO) {
        val event = memoryService.getContextAtTime(
            AgentId.fromString(agentId),
            TenantId.fromString(tenantId),
            Instant.parse(at)
        ) ?: throw IllegalArgumentException("Context not found for agent $agentId at $at")
        ResponseEntity.ok(ContextResponse.from(event, objectMapper))
    }

    @GetMapping("/api/v1/agents/{agentId}/context/diff")
    suspend fun getContextDiff(
        @RequestHeader("X-Tenant-Id") tenantId: String,
        @PathVariable agentId: String,
        @RequestParam from: String,
        @RequestParam to: String
    ): ResponseEntity<ContextDiffResponse> = withContext(Dispatchers.IO) {
        val diff = try {
            memoryService.diffContextWindows(
                AgentId.fromString(agentId),
                TenantId.fromString(tenantId),
                Instant.parse(from),
                Instant.parse(to)
            )
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Context not found for agent $agentId in range [$from, $to]", e)
        }
        ResponseEntity.ok(ContextDiffResponse.from(diff, from, to))
    }

    @GetMapping("/api/v1/sessions/{sessionId}/audit")
    suspend fun getAuditTrail(
        @RequestHeader("X-Tenant-Id") tenantId: String,
        @PathVariable sessionId: String
    ): ResponseEntity<List<AuditEventResponse>> = withContext(Dispatchers.IO) {
        val events = memoryService.getAuditTrail(
            SessionId.fromString(sessionId),
            TenantId.fromString(tenantId)
        )
        ResponseEntity.ok(events.map { AuditEventResponse.from(it, objectMapper) })
    }
}
