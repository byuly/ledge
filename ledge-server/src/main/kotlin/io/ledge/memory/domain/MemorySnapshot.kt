package io.ledge.memory.domain

import io.ledge.shared.AgentId
import io.ledge.shared.EventId
import io.ledge.shared.SnapshotId
import io.ledge.shared.TenantId
import java.time.Instant

class MemorySnapshot(
    val id: SnapshotId,
    val agentId: AgentId,
    val tenantId: TenantId,
    val createdAt: Instant = Instant.now(),
    val parentSnapshotId: SnapshotId? = null,
    val snapshotHash: ContentHash,
    val entries: List<MemoryEntry>,
    val triggerEventId: EventId
)
