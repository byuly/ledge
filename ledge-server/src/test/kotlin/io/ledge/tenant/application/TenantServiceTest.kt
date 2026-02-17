package io.ledge.tenant.application

import io.ledge.TestFixtures
import io.ledge.shared.DomainEvent
import io.ledge.tenant.domain.TenantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TenantServiceTest {

    private lateinit var tenantRepo: FakeTenantRepository
    private lateinit var agentRepo: FakeAgentRepository
    private lateinit var domainEventPublisher: FakeDomainEventPublisher
    private lateinit var service: TenantService

    @BeforeEach
    fun setUp() {
        tenantRepo = FakeTenantRepository()
        agentRepo = FakeAgentRepository()
        domainEventPublisher = FakeDomainEventPublisher()
        service = TenantService(tenantRepo, agentRepo, domainEventPublisher)
    }

    // --- createTenant ---

    @Test
    fun `createTenant saves tenant with correct fields and ACTIVE status`() {
        val command = CreateTenantCommand(name = "Acme Corp", apiKeyHash = "hash123")

        val tenant = service.createTenant(command)

        assertNotNull(tenant.id)
        assertEquals("Acme Corp", tenant.name)
        assertEquals("hash123", tenant.apiKeyHash)
        assertEquals(TenantStatus.ACTIVE, tenant.status)
    }

    @Test
    fun `createTenant generates unique IDs across calls`() {
        val command = CreateTenantCommand(name = "Acme Corp", apiKeyHash = "hash123")

        val t1 = service.createTenant(command)
        val t2 = service.createTenant(command)

        assertNotEquals(t1.id, t2.id)
    }

    @Test
    fun `createTenant persists tenant to repository`() {
        val command = CreateTenantCommand(name = "Acme Corp", apiKeyHash = "hash123")

        val tenant = service.createTenant(command)

        val found = tenantRepo.findById(tenant.id)
        assertNotNull(found)
        assertEquals(tenant.id, found!!.id)
    }

    // --- getTenant ---

    @Test
    fun `getTenant returns null when tenant does not exist`() {
        assertNull(service.getTenant(TestFixtures.tenantId()))
    }

    @Test
    fun `getTenant returns tenant when it exists`() {
        val created = service.createTenant(CreateTenantCommand("Acme", "hash"))

        val found = service.getTenant(created.id)

        assertNotNull(found)
        assertEquals(created.id, found!!.id)
    }

    // --- suspendTenant ---

    @Test
    fun `suspendTenant transitions to SUSPENDED`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))

        val suspended = service.suspendTenant(tenant.id)

        assertEquals(TenantStatus.SUSPENDED, suspended.status)
    }

    @Test
    fun `suspendTenant throws for unknown tenant`() {
        assertThrows<IllegalArgumentException> {
            service.suspendTenant(TestFixtures.tenantId())
        }
    }

    @Test
    fun `suspendTenant throws for already suspended tenant`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))
        service.suspendTenant(tenant.id)

        assertThrows<IllegalStateException> {
            service.suspendTenant(tenant.id)
        }
    }

    // --- deleteTenant ---

    @Test
    fun `deleteTenant transitions to DELETED`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))

        val deleted = service.deleteTenant(tenant.id)

        assertEquals(TenantStatus.DELETED, deleted.status)
    }

    @Test
    fun `deleteTenant hard-deletes all agents for tenant`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))
        val agent1 = service.registerAgent(tenant.id, RegisterAgentCommand("Agent 1", "desc"))
        val agent2 = service.registerAgent(tenant.id, RegisterAgentCommand("Agent 2", "desc"))

        service.deleteTenant(tenant.id)

        assertNull(agentRepo.findById(agent1.id, tenant.id))
        assertNull(agentRepo.findById(agent2.id, tenant.id))
        assertTrue(agentRepo.findByTenantId(tenant.id).isEmpty())
    }

    @Test
    fun `deleteTenant publishes exactly one TenantPurged event`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))

        service.deleteTenant(tenant.id)

        assertEquals(1, domainEventPublisher.publishedEvents.size)
        val event = domainEventPublisher.publishedEvents[0]
        assertTrue(event is DomainEvent.TenantPurged)
        val purged = event as DomainEvent.TenantPurged
        assertEquals(tenant.id, purged.tenantId)
    }

    @Test
    fun `deleteTenant throws for unknown tenant`() {
        assertThrows<IllegalArgumentException> {
            service.deleteTenant(TestFixtures.tenantId())
        }
    }

    @Test
    fun `deleteTenant throws for already deleted tenant`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))
        service.deleteTenant(tenant.id)

        assertThrows<IllegalStateException> {
            service.deleteTenant(tenant.id)
        }
    }

    // --- registerAgent ---

    @Test
    fun `registerAgent creates agent with correct fields and tenant association`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))
        val command = RegisterAgentCommand("My Agent", "Does things", mapOf("env" to "prod"))

        val agent = service.registerAgent(tenant.id, command)

        assertNotNull(agent.id)
        assertEquals(tenant.id, agent.tenantId)
        assertEquals("My Agent", agent.name)
        assertEquals("Does things", agent.description)
        assertEquals(mapOf("env" to "prod"), agent.metadata)
    }

    @Test
    fun `registerAgent generates unique agent IDs`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))
        val command = RegisterAgentCommand("Agent", "desc")

        val a1 = service.registerAgent(tenant.id, command)
        val a2 = service.registerAgent(tenant.id, command)

        assertNotEquals(a1.id, a2.id)
    }

    @Test
    fun `registerAgent throws for unknown tenant`() {
        assertThrows<IllegalArgumentException> {
            service.registerAgent(TestFixtures.tenantId(), RegisterAgentCommand("Agent", "desc"))
        }
    }

    @Test
    fun `registerAgent throws for non-ACTIVE tenant`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))
        service.suspendTenant(tenant.id)

        assertThrows<IllegalStateException> {
            service.registerAgent(tenant.id, RegisterAgentCommand("Agent", "desc"))
        }
    }

    // --- getAgent ---

    @Test
    fun `getAgent returns null when agent does not exist`() {
        assertNull(service.getAgent(TestFixtures.agentId(), TestFixtures.tenantId()))
    }

    @Test
    fun `getAgent returns agent when it exists`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))
        val agent = service.registerAgent(tenant.id, RegisterAgentCommand("Agent", "desc"))

        val found = service.getAgent(agent.id, tenant.id)

        assertNotNull(found)
        assertEquals(agent.id, found!!.id)
    }

    @Test
    fun `getAgent returns null for wrong tenant`() {
        val tenantA = service.createTenant(CreateTenantCommand("Acme A", "hashA"))
        val tenantB = service.createTenant(CreateTenantCommand("Acme B", "hashB"))
        val agent = service.registerAgent(tenantA.id, RegisterAgentCommand("Agent", "desc"))

        assertNull(service.getAgent(agent.id, tenantB.id))
    }

    // --- listAgents ---

    @Test
    fun `listAgents returns empty list when no agents`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))

        val agents = service.listAgents(tenant.id)

        assertTrue(agents.isEmpty())
    }

    @Test
    fun `listAgents returns all agents for tenant`() {
        val tenant = service.createTenant(CreateTenantCommand("Acme", "hash"))
        service.registerAgent(tenant.id, RegisterAgentCommand("Agent 1", "desc"))
        service.registerAgent(tenant.id, RegisterAgentCommand("Agent 2", "desc"))

        val agents = service.listAgents(tenant.id)

        assertEquals(2, agents.size)
    }

    @Test
    fun `listAgents does not include agents from other tenants`() {
        val tenantA = service.createTenant(CreateTenantCommand("Acme A", "hashA"))
        val tenantB = service.createTenant(CreateTenantCommand("Acme B", "hashB"))
        service.registerAgent(tenantA.id, RegisterAgentCommand("Agent A", "desc"))
        service.registerAgent(tenantB.id, RegisterAgentCommand("Agent B", "desc"))

        val agentsA = service.listAgents(tenantA.id)
        val agentsB = service.listAgents(tenantB.id)

        assertEquals(1, agentsA.size)
        assertEquals("Agent A", agentsA[0].name)
        assertEquals(1, agentsB.size)
        assertEquals("Agent B", agentsB[0].name)
    }
}
