package io.ledge.ingestion.api

import io.ledge.ingestion.application.CreateSessionCommand
import io.ledge.ingestion.application.IngestionService
import io.ledge.shared.AgentId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/sessions")
class SessionController(
    private val ingestionService: IngestionService
) {

    @PostMapping
    suspend fun createSession(
        @RequestHeader("X-Tenant-Id") tenantId: String,
        @RequestBody request: CreateSessionRequest
    ): ResponseEntity<SessionResponse> = withContext(Dispatchers.IO) {
        val command = CreateSessionCommand(agentId = AgentId.fromString(request.agentId))
        val session = ingestionService.createSession(TenantId.fromString(tenantId), command)
        ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.from(session))
    }

    @GetMapping("/{sessionId}")
    suspend fun getSession(
        @RequestHeader("X-Tenant-Id") tenantId: String,
        @PathVariable sessionId: String
    ): ResponseEntity<SessionResponse> = withContext(Dispatchers.IO) {
        val session = ingestionService.getSession(
            SessionId.fromString(sessionId),
            TenantId.fromString(tenantId)
        ) ?: throw IllegalArgumentException("Session not found: $sessionId")
        ResponseEntity.ok(SessionResponse.from(session))
    }

    @PatchMapping("/{sessionId}")
    suspend fun updateSession(
        @RequestHeader("X-Tenant-Id") tenantId: String,
        @PathVariable sessionId: String,
        @RequestBody request: UpdateSessionRequest
    ): ResponseEntity<Void> = withContext(Dispatchers.IO) {
        val sid = SessionId.fromString(sessionId)
        val tid = TenantId.fromString(tenantId)
        when (request.status.uppercase()) {
            "COMPLETED" -> ingestionService.completeSession(sid, tid)
            "ABANDONED" -> ingestionService.abandonSession(sid, tid)
            else -> throw IllegalArgumentException("Invalid status: ${request.status}. Must be COMPLETED or ABANDONED")
        }
        ResponseEntity.accepted().build()
    }

    @GetMapping("/{sessionId}/events")
    suspend fun getSessionEvents(
        @RequestHeader("X-Tenant-Id") tenantId: String,
        @PathVariable sessionId: String,
        @RequestParam(required = false) after: String?,
        @RequestParam(required = false, defaultValue = "50") limit: Int
    ): ResponseEntity<List<MemoryEventResponse>> = withContext(Dispatchers.IO) {
        val events = ingestionService.getSessionEvents(
            SessionId.fromString(sessionId),
            TenantId.fromString(tenantId),
            after = after?.let { Instant.parse(it) },
            limit = limit
        )
        ResponseEntity.ok(events.map { MemoryEventResponse.from(it) })
    }
}
