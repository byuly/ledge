package io.ledge.memory.application

import io.ledge.TestFixtures
import io.ledge.memory.domain.MemoryEntryType
import io.ledge.shared.DomainEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class MemoryServiceTest {

    private lateinit var snapshotRepo: FakeMemorySnapshotRepository
    private lateinit var entryRepo: FakeMemoryEntryRepository
    private lateinit var eventPublisher: FakeDomainEventPublisher
    private lateinit var service: MemoryService

    @BeforeEach
    fun setUp() {
        snapshotRepo = FakeMemorySnapshotRepository()
        entryRepo = FakeMemoryEntryRepository()
        eventPublisher = FakeDomainEventPublisher()
        service = MemoryService(snapshotRepo, entryRepo, eventPublisher)
    }

    // --- createSnapshot ---

    @Test
    fun `createSnapshot saves snapshot with correct fields`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val entries = listOf(TestFixtures.memoryEntry(), TestFixtures.memoryEntry())
        val hash = TestFixtures.contentHash()
        val triggerEventId = TestFixtures.eventId()

        val snapshot = service.createSnapshot(agentId, tenantId, entries, hash, triggerEventId)

        assertNotNull(snapshot.id)
        assertEquals(agentId, snapshot.agentId)
        assertEquals(tenantId, snapshot.tenantId)
        assertEquals(entries, snapshot.entries)
        assertEquals(hash, snapshot.snapshotHash)
        assertEquals(triggerEventId, snapshot.triggerEventId)
    }

    @Test
    fun `createSnapshot first snapshot has null parentSnapshotId`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()

        val snapshot = service.createSnapshot(
            agentId, tenantId, emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )

        assertNull(snapshot.parentSnapshotId)
    }

    @Test
    fun `createSnapshot auto-links parentSnapshotId to latest existing snapshot`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()

        val first = service.createSnapshot(
            agentId, tenantId, emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )
        val second = service.createSnapshot(
            agentId, tenantId, emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )

        assertEquals(first.id, second.parentSnapshotId)
    }

    // --- getSnapshot ---

    @Test
    fun `getSnapshot returns null when no snapshots exist`() {
        assertNull(service.getSnapshot(TestFixtures.snapshotId()))
    }

    @Test
    fun `getSnapshot returns saved snapshot`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val saved = service.createSnapshot(
            agentId, tenantId, emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )

        val found = service.getSnapshot(saved.id)

        assertNotNull(found)
        assertEquals(saved.id, found!!.id)
    }

    // --- getLatestSnapshot ---

    @Test
    fun `getLatestSnapshot returns null when no snapshots exist`() {
        assertNull(service.getLatestSnapshot(TestFixtures.agentId(), TestFixtures.tenantId()))
    }

    @Test
    fun `getLatestSnapshot returns the most recent snapshot for agent and tenant`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()

        service.createSnapshot(
            agentId, tenantId, emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )
        val second = service.createSnapshot(
            agentId, tenantId, emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )

        val latest = service.getLatestSnapshot(agentId, tenantId)

        assertNotNull(latest)
        assertEquals(second.id, latest!!.id)
    }

    // --- diffSnapshots ---

    @Test
    fun `diffSnapshots computes added entries`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val newEntry = TestFixtures.memoryEntry()

        val from = service.createSnapshot(
            agentId, tenantId, emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )
        val to = service.createSnapshot(
            agentId, tenantId, listOf(newEntry), TestFixtures.contentHash(), TestFixtures.eventId()
        )

        val diff = service.diffSnapshots(from.id, to.id)

        assertEquals(1, diff.addedEntries.size)
        assertEquals(newEntry.id, diff.addedEntries[0].id)
        assertTrue(diff.removedEntries.isEmpty())
        assertTrue(diff.modifiedEntries.isEmpty())
    }

    @Test
    fun `diffSnapshots computes removed entries`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val oldEntry = TestFixtures.memoryEntry()

        val from = service.createSnapshot(
            agentId, tenantId, listOf(oldEntry), TestFixtures.contentHash(), TestFixtures.eventId()
        )
        val to = service.createSnapshot(
            agentId, tenantId, emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )

        val diff = service.diffSnapshots(from.id, to.id)

        assertTrue(diff.addedEntries.isEmpty())
        assertEquals(1, diff.removedEntries.size)
        assertEquals(oldEntry.id, diff.removedEntries[0].id)
        assertTrue(diff.modifiedEntries.isEmpty())
    }

    @Test
    fun `diffSnapshots computes modified entries`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val entryId = TestFixtures.entryId()
        val oldEntry = TestFixtures.memoryEntry(id = entryId, content = "old content")
        val newEntry = TestFixtures.memoryEntry(id = entryId, content = "new content")

        val from = service.createSnapshot(
            agentId, tenantId, listOf(oldEntry), TestFixtures.contentHash(), TestFixtures.eventId()
        )
        val to = service.createSnapshot(
            agentId, tenantId, listOf(newEntry), TestFixtures.contentHash(), TestFixtures.eventId()
        )

        val diff = service.diffSnapshots(from.id, to.id)

        assertTrue(diff.addedEntries.isEmpty())
        assertTrue(diff.removedEntries.isEmpty())
        assertEquals(1, diff.modifiedEntries.size)
        assertEquals(entryId, diff.modifiedEntries[0].entryId)
        assertEquals("old content", diff.modifiedEntries[0].before.content)
        assertEquals("new content", diff.modifiedEntries[0].after.content)
    }

    @Test
    fun `diffSnapshots returns empty diff when snapshots have identical entries`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val entry = TestFixtures.memoryEntry()

        val from = service.createSnapshot(
            agentId, tenantId, listOf(entry), TestFixtures.contentHash(), TestFixtures.eventId()
        )
        val to = service.createSnapshot(
            agentId, tenantId, listOf(entry), TestFixtures.contentHash(), TestFixtures.eventId()
        )

        val diff = service.diffSnapshots(from.id, to.id)

        assertTrue(diff.addedEntries.isEmpty())
        assertTrue(diff.removedEntries.isEmpty())
        assertTrue(diff.modifiedEntries.isEmpty())
    }

    @Test
    fun `diffSnapshots throws when snapshot not found`() {
        val existingSnapshot = service.createSnapshot(
            TestFixtures.agentId(), TestFixtures.tenantId(),
            emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )
        val missingId = TestFixtures.snapshotId()

        assertThrows<IllegalArgumentException> {
            service.diffSnapshots(missingId, existingSnapshot.id)
        }
        assertThrows<IllegalArgumentException> {
            service.diffSnapshots(existingSnapshot.id, missingId)
        }
    }

    // --- recordAccess ---

    @Test
    fun `recordAccess increments accessCount and sets lastAccessedAt`() {
        val entry = TestFixtures.memoryEntry(accessCount = 0, lastAccessedAt = null)
        entryRepo.save(entry)

        val updated = service.recordAccess(entry.id)

        assertNotNull(updated)
        assertEquals(1, updated!!.accessCount)
        assertNotNull(updated.lastAccessedAt)
    }

    @Test
    fun `recordAccess returns null for unknown entryId`() {
        assertNull(service.recordAccess(TestFixtures.entryId()))
    }

    // --- purgeTenantMemory ---

    @Test
    fun `purgeTenantMemory deletes all snapshots and entries for tenant`() {
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()
        val entry = TestFixtures.memoryEntry()

        service.createSnapshot(
            agentId, tenantId, listOf(entry), TestFixtures.contentHash(), TestFixtures.eventId()
        )
        entryRepo.save(entry)
        entryRepo.associateWithTenant(entry.id, tenantId)

        service.purgeTenantMemory(tenantId)

        assertNull(service.getLatestSnapshot(agentId, tenantId))
        assertNull(entryRepo.findById(entry.id))
    }

    @Test
    fun `purgeTenantMemory publishes exactly one TenantPurged event`() {
        val tenantId = TestFixtures.tenantId()

        service.purgeTenantMemory(tenantId)

        assertEquals(1, eventPublisher.publishedEvents.size)
        val event = eventPublisher.publishedEvents[0]
        assertTrue(event is DomainEvent.TenantPurged)
        assertEquals(tenantId, (event as DomainEvent.TenantPurged).tenantId)
    }

    @Test
    fun `purgeTenantMemory does not affect other tenants data`() {
        val tenantA = TestFixtures.tenantId()
        val tenantB = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()

        service.createSnapshot(
            agentId, tenantA, emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )
        val snapshotB = service.createSnapshot(
            agentId, tenantB, emptyList(), TestFixtures.contentHash(), TestFixtures.eventId()
        )

        service.purgeTenantMemory(tenantA)

        assertNull(service.getLatestSnapshot(agentId, tenantA))
        assertNotNull(service.getLatestSnapshot(agentId, tenantB))
        assertEquals(snapshotB.id, service.getLatestSnapshot(agentId, tenantB)!!.id)
    }
}
