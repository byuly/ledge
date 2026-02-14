package io.ledge.shared

import java.util.UUID

@JvmInline
value class EntryId(val value: UUID) {
    companion object {
        fun generate(): EntryId = EntryId(UUID.randomUUID())
        fun fromString(id: String): EntryId = EntryId(UUID.fromString(id))
    }

    override fun toString(): String = value.toString()
}
