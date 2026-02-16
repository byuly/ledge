package io.ledge.tenant.api

import io.ledge.TestFixtures
import io.ledge.infrastructure.api.GlobalExceptionHandler
import io.ledge.tenant.application.CreateTenantCommand
import io.ledge.tenant.application.FakeAgentRepository
import io.ledge.tenant.application.FakeDomainEventPublisher
import io.ledge.tenant.application.FakeTenantRepository
import io.ledge.tenant.application.RegisterAgentCommand
import io.ledge.tenant.application.TenantService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

class TenantControllerTest {

    private lateinit var tenantRepo: FakeTenantRepository
    private lateinit var agentRepo: FakeAgentRepository
    private lateinit var service: TenantService
    private lateinit var client: WebTestClient

    @BeforeEach
    fun setUp() {
        tenantRepo = FakeTenantRepository()
        agentRepo = FakeAgentRepository()
        service = TenantService(tenantRepo, agentRepo, FakeDomainEventPublisher())
        val controller = TenantController(service)
        client = WebTestClient.bindToController(controller)
            .controllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // --- POST /api/v1/tenants ---

    @Test
    fun `createTenant returns 201 with tenant details`() {
        client.post().uri("/api/v1/tenants")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "name": "Acme Corp",
                    "apiKeyHash": "${TestFixtures.VALID_SHA256}"
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.tenantId").isNotEmpty
            .jsonPath("$.name").isEqualTo("Acme Corp")
            .jsonPath("$.status").isEqualTo("ACTIVE")
    }

    // --- DELETE /api/v1/tenants/{tenantId} ---

    @Test
    fun `deleteTenant returns 204 when tenant exists`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme Corp", TestFixtures.VALID_SHA256))

        client.delete().uri("/api/v1/tenants/${tenant.id.value}")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `deleteTenant returns 404 when tenant not found`() {
        client.delete().uri("/api/v1/tenants/${TestFixtures.tenantId().value}")
            .exchange()
            .expectStatus().isNotFound
    }

    // --- POST /api/v1/agents ---

    @Test
    fun `registerAgent returns 201 with agent details`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme Corp", TestFixtures.VALID_SHA256))

        client.post().uri("/api/v1/agents")
            .header("X-Tenant-Id", tenant.id.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "name": "My Agent",
                    "description": "Does things",
                    "metadata": {"version": "1.0"}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.agentId").isNotEmpty
            .jsonPath("$.tenantId").isEqualTo(tenant.id.value.toString())
            .jsonPath("$.name").isEqualTo("My Agent")
            .jsonPath("$.description").isEqualTo("Does things")
            .jsonPath("$.metadata.version").isEqualTo("1.0")
    }

    @Test
    fun `registerAgent returns 409 when tenant is not active`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme Corp", TestFixtures.VALID_SHA256))
        service.suspendTenant(tenant.id)

        client.post().uri("/api/v1/agents")
            .header("X-Tenant-Id", tenant.id.value.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                    "name": "My Agent",
                    "description": "Does things"
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Conflict")
    }

    // --- GET /api/v1/agents ---

    @Test
    fun `listAgents returns 200 with agent list`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme Corp", TestFixtures.VALID_SHA256))
        service.registerAgent(tenant.id, RegisterAgentCommand("Agent 1", "First agent"))
        service.registerAgent(tenant.id, RegisterAgentCommand("Agent 2", "Second agent"))

        client.get().uri("/api/v1/agents")
            .header("X-Tenant-Id", tenant.id.value.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
    }
}
