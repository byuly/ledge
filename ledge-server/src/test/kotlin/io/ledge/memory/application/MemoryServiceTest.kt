package io.ledge.memory.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ledge.TestFixtures
import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.memory.domain.ContentBlock
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
    private lateinit var observationQuery: FakeObservationEventQuery
    private lateinit var service: MemoryService

    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        snapshotRepo = FakeMemorySnapshotRepository()
        entryRepo = FakeMemoryEntryRepository()
        eventPublisher = FakeDomainEventPublisher()
        observationQuery = FakeObservationEventQuery()
        service = MemoryService(snapshotRepo, entryRepo, eventPublisher, observationQuery)
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

    // --- getContextAtTime ---

    @Test
    fun `getContextAtTime returns null when no events exist`() {
        assertNull(
            service.getContextAtTime(TestFixtures.agentId(), TestFixtures.tenantId(), Instant.now())
        )
    }

    @Test
    fun `getContextAtTime returns latest CONTEXT_ASSEMBLED before timestamp`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val t1 = Instant.parse("2025-01-01T00:00:00Z")
        val t2 = Instant.parse("2025-01-01T01:00:00Z")

        val event = contextAssembledEvent(agentId, tenantId, t1)
        observationQuery.addEvent(event)

        val result = service.getContextAtTime(agentId, tenantId, t2)

        assertNotNull(result)
        assertEquals(event.id, result!!.id)
    }

    @Test
    fun `getContextAtTime returns null when all events are after timestamp`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val t1 = Instant.parse("2025-01-01T01:00:00Z")
        val tBefore = Instant.parse("2025-01-01T00:00:00Z")

        observationQuery.addEvent(contextAssembledEvent(agentId, tenantId, t1))

        assertNull(service.getContextAtTime(agentId, tenantId, tBefore))
    }

    // --- getAuditTrail ---

    @Test
    fun `getAuditTrail returns events in sequence order`() {
        val sessionId = TestFixtures.sessionId()
        val tenantId = TestFixtures.tenantId()
        val agentId = TestFixtures.agentId()

        val e1 = memoryEvent(sessionId, agentId, tenantId, EventType.USER_INPUT, 1L)
        val e2 = memoryEvent(sessionId, agentId, tenantId, EventType.AGENT_OUTPUT, 2L)
        observationQuery.addEvent(e2)
        observationQuery.addEvent(e1)

        val trail = service.getAuditTrail(sessionId, tenantId)

        assertEquals(2, trail.size)
        assertEquals(1L, trail[0].sequenceNumber)
        assertEquals(2L, trail[1].sequenceNumber)
    }

    @Test
    fun `getAuditTrail returns empty for no events`() {
        assertTrue(
            service.getAuditTrail(TestFixtures.sessionId(), TestFixtures.tenantId()).isEmpty()
        )
    }

    // --- diffContextWindows ---

    @Test
    fun `diffContextWindows throws when no event found`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val t1 = Instant.parse("2025-01-01T00:00:00Z")
        val t2 = Instant.parse("2025-01-01T01:00:00Z")

        assertThrows<IllegalArgumentException> {
            service.diffContextWindows(agentId, tenantId, t1, t2)
        }
    }

    @Test
    fun `diffContextWindows computes added, removed, and modified blocks`() {
        val agentId = TestFixtures.agentId()
        val tenantId = TestFixtures.tenantId()
        val t1 = Instant.parse("2025-01-01T00:00:00Z")
        val t2 = Instant.parse("2025-01-01T01:00:00Z")
        val tQuery1 = Instant.parse("2025-01-01T00:30:00Z")
        val tQuery2 = Instant.parse("2025-01-01T01:30:00Z")

        val fromBlocks = listOf(
            ContentBlock("system", "You are helpful", 3, null),
            ContentBlock("user", "Hello", 1, null)
        )
        val toBlocks = listOf(
            ContentBlock("system", "You are very helpful", 4, null),
            ContentBlock("document", "New document", 2, "rag")
        )

        val fromEvent = contextAssembledEvent(agentId, tenantId, t1, objectMapper.writeValueAsString(fromBlocks))
        val toEvent = contextAssembledEvent(agentId, tenantId, t2, objectMapper.writeValueAsString(toBlocks))
        observationQuery.addEvent(fromEvent)
        observationQuery.addEvent(toEvent)

        val diff = service.diffContextWindows(agentId, tenantId, tQuery1, tQuery2)

        assertEquals(fromEvent.id, diff.fromEventId)
        assertEquals(toEvent.id, diff.toEventId)
        assertEquals(1, diff.addedBlocks.size)
        assertEquals("document", diff.addedBlocks[0].blockType)
        assertEquals(1, diff.removedBlocks.size)
        assertEquals("user", diff.removedBlocks[0].blockType)
        assertEquals(1, diff.modifiedBlocks.size)
        assertEquals("system", diff.modifiedBlocks[0].blockType)
        assertEquals("You are helpful", diff.modifiedBlocks[0].before.content)
        assertEquals("You are very helpful", diff.modifiedBlocks[0].after.content)
    }

    // --- Test helpers ---

    private fun contextAssembledEvent(
        agentId: io.ledge.shared.AgentId,
        tenantId: io.ledge.shared.TenantId,
        occurredAt: Instant,
        payload: String = "[]"
    ): MemoryEvent = MemoryEvent(
        id = TestFixtures.eventId(),
        sessionId = TestFixtures.sessionId(),
        agentId = agentId,
        tenantId = tenantId,
        eventType = EventType.CONTEXT_ASSEMBLED,
        sequenceNumber = 1L,
        occurredAt = occurredAt,
        payload = payload,
        contextHash = null,
        parentEventId = null,
        schemaVersion = SchemaVersion(1)
    )

    private fun memoryEvent(
        sessionId: io.ledge.shared.SessionId,
        agentId: io.ledge.shared.AgentId,
        tenantId: io.ledge.shared.TenantId,
        eventType: EventType,
        sequenceNumber: Long
    ): MemoryEvent = MemoryEvent(
        id = TestFixtures.eventId(),
        sessionId = sessionId,
        agentId = agentId,
        tenantId = tenantId,
        eventType = eventType,
        sequenceNumber = sequenceNumber,
        occurredAt = Instant.now(),
        payload = "{}",
        contextHash = null,
        parentEventId = null,
        schemaVersion = SchemaVersion(1)
    )
}
