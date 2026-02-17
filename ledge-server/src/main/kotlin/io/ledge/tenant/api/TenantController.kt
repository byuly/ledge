package io.ledge.tenant.api

import io.ledge.shared.TenantId
import io.ledge.tenant.application.CreateTenantCommand
import io.ledge.tenant.application.RegisterAgentCommand
import io.ledge.tenant.application.TenantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class TenantController(
    private val tenantService: TenantService
) {

    @PostMapping("/tenants")
    suspend fun createTenant(
        @RequestBody request: CreateTenantRequest
    ): ResponseEntity<TenantResponse> = withContext(Dispatchers.IO) {
        val command = CreateTenantCommand(name = request.name, apiKeyHash = request.apiKeyHash)
        val tenant = tenantService.createTenant(command)
        ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(tenant))
    }

    @DeleteMapping("/tenants/{tenantId}")
    suspend fun deleteTenant(
        @PathVariable tenantId: String
    ): ResponseEntity<Void> = withContext(Dispatchers.IO) {
        tenantService.deleteTenant(TenantId.fromString(tenantId))
        ResponseEntity.noContent().build()
    }

    @PostMapping("/agents")
    suspend fun registerAgent(
        @RequestHeader("X-Tenant-Id") tenantId: String,
        @RequestBody request: RegisterAgentRequest
    ): ResponseEntity<AgentResponse> = withContext(Dispatchers.IO) {
        val command = RegisterAgentCommand(
            name = request.name,
            description = request.description,
            metadata = request.metadata
        )
        val agent = tenantService.registerAgent(TenantId.fromString(tenantId), command)
        ResponseEntity.status(HttpStatus.CREATED).body(AgentResponse.from(agent))
    }

    @GetMapping("/agents")
    suspend fun listAgents(
        @RequestHeader("X-Tenant-Id") tenantId: String
    ): ResponseEntity<List<AgentResponse>> = withContext(Dispatchers.IO) {
        val agents = tenantService.listAgents(TenantId.fromString(tenantId))
        ResponseEntity.ok(agents.map { AgentResponse.from(it) })
    }
}
