package io.ledge.tenant.domain

import io.ledge.TestFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class AgentTest {

    @Test
    fun `construction with all fields`() {
        val id = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val metadata = mapOf("env" to "prod")

        val agent = Agent(
            id = id,
            tenantId = tenantId,
            name = "Coding Agent",
            description = "Assists with coding tasks",
            metadata = metadata
        )

        assertEquals(id, agent.id)
        assertEquals(tenantId, agent.tenantId)
        assertEquals("Coding Agent", agent.name)
        assertEquals("Assists with coding tasks", agent.description)
        assertEquals(metadata, agent.metadata)
    }

    @Test
    fun `default metadata is empty map`() {
        val agent = Agent(
            id = TestFixtures.agentId(),
            tenantId = TestFixtures.tenantId(),
            name = "Agent",
            description = "Desc"
        )

        assertTrue(agent.metadata.isEmpty())
    }

    @Test
    fun `data class equality`() {
        val id = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val createdAt = java.time.Instant.parse("2025-01-01T00:00:00Z")

        val agent1 = Agent(id = id, tenantId = tenantId, name = "A", description = "D", createdAt = createdAt)
        val agent2 = Agent(id = id, tenantId = tenantId, name = "A", description = "D", createdAt = createdAt)

        assertEquals(agent1, agent2)
    }
}
