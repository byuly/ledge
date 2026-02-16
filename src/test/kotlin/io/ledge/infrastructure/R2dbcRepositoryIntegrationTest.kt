package io.ledge.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.TestFixtures
import io.ledge.ingestion.domain.SessionStatus
import io.ledge.ingestion.infrastructure.R2dbcSessionRepository
import io.ledge.tenant.domain.TenantStatus
import io.ledge.tenant.infrastructure.R2dbcAgentRepository
import io.ledge.tenant.infrastructure.R2dbcTenantRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient

@Tag("integration")
class R2dbcRepositoryIntegrationTest {

    private val db: DatabaseClient = TestContainers.buildDatabaseClient()
    private val objectMapper: ObjectMapper = ObjectMapper()
    private val tenantRepo = R2dbcTenantRepository(db)
    private val agentRepo = R2dbcAgentRepository(db, objectMapper)
    private val sessionRepo = R2dbcSessionRepository(db)

    @BeforeEach
    fun cleanTables() {
        db.sql("DELETE FROM sessions").then().block()
        db.sql("DELETE FROM agents").then().block()
        db.sql("DELETE FROM tenants").then().block()
    }

    @Test
    fun `save and find tenant by id`() {
        val tenant = TestFixtures.tenant()
        tenantRepo.save(tenant)

        val found = tenantRepo.findById(tenant.id)
        assertNotNull(found)
        assertEquals(tenant.id, found!!.id)
        assertEquals(tenant.name, found.name)
        assertEquals(tenant.apiKeyHash, found.apiKeyHash)
        assertEquals(TenantStatus.ACTIVE, found.status)
    }

    @Test
    fun `find tenant by api key hash`() {
        val tenant = TestFixtures.tenant(apiKeyHash = "unique_hash_value_for_test_1234567890abcdef1234567890abcdef")
        tenantRepo.save(tenant)

        val found = tenantRepo.findByApiKeyHash(tenant.apiKeyHash)
        assertNotNull(found)
        assertEquals(tenant.id, found!!.id)
    }

    @Test
    fun `find tenant by api key hash returns null when not found`() {
        assertNull(tenantRepo.findByApiKeyHash("nonexistent"))
    }

    @Test
    fun `upsert tenant updates mutable fields`() {
        val tenant = TestFixtures.tenant()
        tenantRepo.save(tenant)

        tenant.suspend()
        tenantRepo.save(tenant)

        val found = tenantRepo.findById(tenant.id)
        assertEquals(TenantStatus.SUSPENDED, found!!.status)
    }

    @Test
    fun `save and find agent with metadata JSONB round-trip`() {
        val tenant = TestFixtures.tenant()
        tenantRepo.save(tenant)

        val metadata = mapOf("model" to "gpt-4", "version" to "1.0")
        val agent = TestFixtures.agent(tenantId = tenant.id, metadata = metadata)
        agentRepo.save(agent)

        val found = agentRepo.findById(agent.id, tenant.id)
        assertNotNull(found)
        assertEquals(agent.id, found!!.id)
        assertEquals(metadata, found.metadata)
    }

    @Test
    fun `agent findById enforces tenant isolation`() {
        val tenant1 = TestFixtures.tenant()
        val tenant2 = TestFixtures.tenant(apiKeyHash = "other_hash_1234567890abcdef1234567890abcdef1234567890abcdef12")
        tenantRepo.save(tenant1)
        tenantRepo.save(tenant2)

        val agent = TestFixtures.agent(tenantId = tenant1.id)
        agentRepo.save(agent)

        assertNotNull(agentRepo.findById(agent.id, tenant1.id))
        assertNull(agentRepo.findById(agent.id, tenant2.id))
    }

    @Test
    fun `findByTenantId returns all agents for tenant`() {
        val tenant = TestFixtures.tenant()
        tenantRepo.save(tenant)

        val agent1 = TestFixtures.agent(tenantId = tenant.id, name = "Agent 1")
        val agent2 = TestFixtures.agent(tenantId = tenant.id, name = "Agent 2")
        agentRepo.save(agent1)
        agentRepo.save(agent2)

        val agents = agentRepo.findByTenantId(tenant.id)
        assertEquals(2, agents.size)
    }

    @Test
    fun `deleteByTenantId removes all agents`() {
        val tenant = TestFixtures.tenant()
        tenantRepo.save(tenant)

        agentRepo.save(TestFixtures.agent(tenantId = tenant.id))
        agentRepo.save(TestFixtures.agent(tenantId = tenant.id))

        agentRepo.deleteByTenantId(tenant.id)
        assertEquals(0, agentRepo.findByTenantId(tenant.id).size)
    }

    @Test
    fun `save and find session`() {
        val tenant = TestFixtures.tenant()
        tenantRepo.save(tenant)
        val agent = TestFixtures.agent(tenantId = tenant.id)
        agentRepo.save(agent)

        val session = TestFixtures.session(agentId = agent.id, tenantId = tenant.id)
        sessionRepo.save(session)

        val found = sessionRepo.findById(session.id, tenant.id)
        assertNotNull(found)
        assertEquals(session.id, found!!.id)
        assertEquals(session.agentId, found.agentId)
        assertEquals(SessionStatus.ACTIVE, found.status)
        assertNull(found.endedAt)
        assertEquals(1L, found.nextSequenceNumber)
    }

    @Test
    fun `session upsert updates mutable fields`() {
        val tenant = TestFixtures.tenant()
        tenantRepo.save(tenant)
        val agent = TestFixtures.agent(tenantId = tenant.id)
        agentRepo.save(agent)

        val session = TestFixtures.session(agentId = agent.id, tenantId = tenant.id)
        sessionRepo.save(session)

        session.complete()
        sessionRepo.save(session)

        val found = sessionRepo.findById(session.id, tenant.id)
        assertEquals(SessionStatus.COMPLETED, found!!.status)
        assertNotNull(found.endedAt)
    }

    @Test
    fun `findByAgentId returns sessions for agent and tenant`() {
        val tenant = TestFixtures.tenant()
        tenantRepo.save(tenant)
        val agent = TestFixtures.agent(tenantId = tenant.id)
        agentRepo.save(agent)

        sessionRepo.save(TestFixtures.session(agentId = agent.id, tenantId = tenant.id))
        sessionRepo.save(TestFixtures.session(agentId = agent.id, tenantId = tenant.id))

        val sessions = sessionRepo.findByAgentId(agent.id, tenant.id)
        assertEquals(2, sessions.size)
    }

    @Test
    fun `session deleteByTenantId removes all sessions`() {
        val tenant = TestFixtures.tenant()
        tenantRepo.save(tenant)
        val agent = TestFixtures.agent(tenantId = tenant.id)
        agentRepo.save(agent)

        sessionRepo.save(TestFixtures.session(agentId = agent.id, tenantId = tenant.id))

        sessionRepo.deleteByTenantId(tenant.id)
        assertEquals(0, sessionRepo.findByAgentId(agent.id, tenant.id).size)
    }

    @Test
    fun `session nextSequenceNumber persists across save-load cycle`() {
        val tenant = TestFixtures.tenant()
        tenantRepo.save(tenant)
        val agent = TestFixtures.agent(tenantId = tenant.id)
        agentRepo.save(agent)

        val session = TestFixtures.session(agentId = agent.id, tenantId = tenant.id, nextSequenceNumber = 42L)
        sessionRepo.save(session)

        val found = sessionRepo.findById(session.id, tenant.id)
        assertEquals(42L, found!!.nextSequenceNumber)
    }
}
