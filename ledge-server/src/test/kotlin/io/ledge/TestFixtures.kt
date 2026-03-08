package io.ledge

import io.ledge.ingestion.application.IngestEventCommand
import io.ledge.ingestion.domain.ContextHash
import io.ledge.ingestion.domain.EventType
import io.ledge.ingestion.domain.SchemaVersion
import io.ledge.ingestion.domain.Session
import io.ledge.ingestion.domain.SessionStatus
import io.ledge.memory.domain.Confidence
import io.ledge.memory.domain.ContentBlock
import io.ledge.memory.domain.ContentBlockDelta
import io.ledge.memory.domain.ContentHash
import io.ledge.memory.domain.MemoryEntry
import io.ledge.memory.domain.MemoryEntryType
import io.ledge.memory.domain.MemorySnapshot
import io.ledge.memory.domain.ObservationDiff
import io.ledge.shared.*
import io.ledge.tenant.application.RegisterAgentCommand
import io.ledge.tenant.domain.Agent
import io.ledge.tenant.domain.Tenant
import io.ledge.tenant.domain.TenantStatus
import java.time.Instant

/**
 * Shared factory methods for test objects across all bounded contexts.
 * Each call generates unique IDs to avoid cross-test interference.
 */
object TestFixtures {

    /** 64-char lowercase hex — valid for both ContextHash and ContentHash. */
    const val VALID_SHA256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"

    // Typed ID generators — each call returns a new random UUID-backed ID
    fun tenantId(): TenantId = TenantId.generate()
    fun agentId(): AgentId = AgentId.generate()
    fun sessionId(): SessionId = SessionId.generate()
    fun eventId(): EventId = EventId.generate()
    fun snapshotId(): SnapshotId = SnapshotId.generate()
    fun entryId(): EntryId = EntryId.generate()

    fun contextHash(): ContextHash = ContextHash(VALID_SHA256)
    fun contentHash(): ContentHash = ContentHash(VALID_SHA256)
    fun confidence(value: Float = 0.8f): Confidence = Confidence(value)
    fun schemaVersion(value: Int = 1): SchemaVersion = SchemaVersion(value)

    fun session(
        id: SessionId = sessionId(),
        agentId: AgentId = agentId(),
        tenantId: TenantId = tenantId(),
        startedAt: Instant = Instant.now(),
        endedAt: Instant? = null,
        status: SessionStatus = SessionStatus.ACTIVE
    ): Session = Session(
        id = id,
        agentId = agentId,
        tenantId = tenantId,
        startedAt = startedAt,
        endedAt = endedAt,
        status = status
    )

    fun ingestEventCommand(
        sessionId: SessionId = sessionId(),
        eventType: EventType = EventType.USER_INPUT,
        payload: String = """{"text": "hello"}""",
        occurredAt: Instant = Instant.now(),
        contextHash: ContextHash? = null,
        parentEventId: EventId? = null,
        schemaVersion: SchemaVersion = SchemaVersion(1)
    ): IngestEventCommand = IngestEventCommand(
        sessionId = sessionId,
        eventType = eventType,
        payload = payload,
        occurredAt = occurredAt,
        contextHash = contextHash,
        parentEventId = parentEventId,
        schemaVersion = schemaVersion
    )

    fun memorySnapshot(
        id: SnapshotId = snapshotId(),
        agentId: AgentId = agentId(),
        tenantId: TenantId = tenantId(),
        createdAt: Instant = Instant.now(),
        parentSnapshotId: SnapshotId? = null,
        snapshotHash: ContentHash = contentHash(),
        entries: List<MemoryEntry> = emptyList(),
        triggerEventId: EventId = eventId()
    ): MemorySnapshot = MemorySnapshot(
        id = id,
        agentId = agentId,
        tenantId = tenantId,
        createdAt = createdAt,
        parentSnapshotId = parentSnapshotId,
        snapshotHash = snapshotHash,
        entries = entries,
        triggerEventId = triggerEventId
    )

    fun tenant(
        id: TenantId = tenantId(),
        name: String = "Test Tenant",
        apiKeyHash: String = VALID_SHA256,
        status: TenantStatus = TenantStatus.ACTIVE,
        createdAt: Instant = Instant.now()
    ): Tenant = Tenant(
        id = id,
        name = name,
        apiKeyHash = apiKeyHash,
        status = status,
        createdAt = createdAt
    )

    fun agent(
        id: AgentId = agentId(),
        tenantId: TenantId = tenantId(),
        name: String = "Test Agent",
        description: String = "A test agent",
        metadata: Map<String, String> = emptyMap(),
        createdAt: Instant = Instant.now()
    ): Agent = Agent(
        id = id,
        tenantId = tenantId,
        name = name,
        description = description,
        createdAt = createdAt,
        metadata = metadata
    )

    fun registerAgentCommand(
        name: String = "Test Agent",
        description: String = "A test agent",
        metadata: Map<String, String> = emptyMap()
    ): RegisterAgentCommand = RegisterAgentCommand(
        name = name,
        description = description,
        metadata = metadata
    )

    fun memoryEntry(
        id: EntryId = entryId(),
        content: String = "The user prefers dark mode",
        contentHash: ContentHash = contentHash(),
        entryType: MemoryEntryType = MemoryEntryType.PREFERENCE,
        confidence: Confidence = confidence(),
        sourceEventId: EventId = eventId(),
        createdAt: Instant = Instant.now(),
        expiresAt: Instant? = null,
        accessCount: Long = 0,
        lastAccessedAt: Instant? = null
    ): MemoryEntry = MemoryEntry(
        id = id,
        content = content,
        contentHash = contentHash,
        entryType = entryType,
        confidence = confidence,
        sourceEventId = sourceEventId,
        createdAt = createdAt,
        expiresAt = expiresAt,
        accessCount = accessCount,
        lastAccessedAt = lastAccessedAt
    )

    fun contentBlock(
        blockType: String = "user",
        content: String = "Hello, world!",
        tokenCount: Int = 3,
        source: String? = null
    ): ContentBlock = ContentBlock(
        blockType = blockType,
        content = content,
        tokenCount = tokenCount,
        source = source
    )

    fun contentBlockDelta(
        blockType: String = "user",
        before: ContentBlock = contentBlock(blockType = blockType, content = "before"),
        after: ContentBlock = contentBlock(blockType = blockType, content = "after")
    ): ContentBlockDelta = ContentBlockDelta(
        blockType = blockType,
        before = before,
        after = after
    )

    fun observationDiff(
        fromEventId: EventId = eventId(),
        toEventId: EventId = eventId(),
        addedBlocks: List<ContentBlock> = emptyList(),
        removedBlocks: List<ContentBlock> = emptyList(),
        modifiedBlocks: List<ContentBlockDelta> = emptyList()
    ): ObservationDiff = ObservationDiff(
        fromEventId = fromEventId,
        toEventId = toEventId,
        addedBlocks = addedBlocks,
        removedBlocks = removedBlocks,
        modifiedBlocks = modifiedBlocks
    )
}
