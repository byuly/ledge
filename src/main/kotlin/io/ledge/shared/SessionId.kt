package io.ledge.shared

import java.util.UUID

@JvmInline
value class SessionId(val value: UUID) {
    companion object {
        fun generate(): SessionId = SessionId(UUID.randomUUID())
        fun fromString(id: String): SessionId = SessionId(UUID.fromString(id))
    }

    override fun toString(): String = value.toString()
}
