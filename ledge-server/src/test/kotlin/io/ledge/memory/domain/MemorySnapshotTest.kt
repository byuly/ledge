package io.ledge.memory.domain

import io.ledge.TestFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

class MemorySnapshotTest {

    @Test
    fun `construction with all fields including entries list`() {
        val id = TestFixtures.snapshotId()
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val parentSnapshotId = TestFixtures.snapshotId()
        val snapshotHash = TestFixtures.contentHash()
        val triggerEventId = TestFixtures.eventId()
        val entries = listOf(TestFixtures.memoryEntry(), TestFixtures.memoryEntry())

        val snapshot = MemorySnapshot(
            id = id,
            agentId = agentId,
            tenantId = tenantId,
            parentSnapshotId = parentSnapshotId,
            snapshotHash = snapshotHash,
            entries = entries,
            triggerEventId = triggerEventId
        )

        assertEquals(id, snapshot.id)
        assertEquals(agentId, snapshot.agentId)
        assertEquals(tenantId, snapshot.tenantId)
        assertNotNull(snapshot.parentSnapshotId)
        assertEquals(snapshotHash, snapshot.snapshotHash)
        assertEquals(2, snapshot.entries.size)
        assertEquals(triggerEventId, snapshot.triggerEventId)
    }

    @Test
    fun `construction with null parentSnapshotId for first snapshot in chain`() {
        val snapshot = MemorySnapshot(
            id = TestFixtures.snapshotId(),
            agentId = TestFixtures.agentId(),
            tenantId = TestFixtures.tenantId(),
            parentSnapshotId = null,
            snapshotHash = TestFixtures.contentHash(),
            entries = emptyList(),
            triggerEventId = TestFixtures.eventId()
        )

        assertNull(snapshot.parentSnapshotId)
    }
}
