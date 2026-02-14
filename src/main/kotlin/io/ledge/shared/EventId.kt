package io.ledge.shared

import java.util.UUID

@JvmInline
value class EventId(val value: UUID) {
    companion object {
        fun generate(): EventId = EventId(UUID.randomUUID())
        fun fromString(id: String): EventId = EventId(UUID.fromString(id))
    }

    override fun toString(): String = value.toString()
}
