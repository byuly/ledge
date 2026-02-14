package io.ledge.shared

import java.util.UUID

@JvmInline
value class AgentId(val value: UUID) {
    companion object {
        fun generate(): AgentId = AgentId(UUID.randomUUID())
        fun fromString(id: String): AgentId = AgentId(UUID.fromString(id))
    }

    override fun toString(): String = value.toString()
}
