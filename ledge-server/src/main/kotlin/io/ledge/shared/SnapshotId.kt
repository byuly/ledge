package io.ledge.shared

import java.util.UUID

@JvmInline
value class SnapshotId(val value: UUID) {
    companion object {
        fun generate(): SnapshotId = SnapshotId(UUID.randomUUID())
        fun fromString(id: String): SnapshotId = SnapshotId(UUID.fromString(id))
    }

    override fun toString(): String = value.toString()
}
