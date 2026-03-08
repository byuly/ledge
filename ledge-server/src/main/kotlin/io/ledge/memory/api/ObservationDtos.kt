package io.ledge.memory.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.memory.domain.ContentBlock
import io.ledge.memory.domain.ContentBlockDelta
import io.ledge.memory.domain.ObservationDiff

data class ContextResponse(
    val eventId: String,
    val agentId: String,
    val occurredAt: String,
    val payload: JsonNode,
    val contextHash: String?
) {
    companion object {
        fun from(event: MemoryEvent, objectMapper: ObjectMapper): ContextResponse = ContextResponse(
            eventId = event.id.value.toString(),
            agentId = event.agentId.value.toString(),
            occurredAt = event.occurredAt.toString(),
            payload = objectMapper.readTree(event.payload),
            contextHash = event.contextHash?.value
        )
    }
}

data class ContextDiffResponse(
    val fromEventId: String,
    val toEventId: String,
    val from: String,
    val to: String,
    val addedBlocks: List<ContentBlockDto>,
    val removedBlocks: List<ContentBlockDto>,
    val modifiedBlocks: List<ContentBlockDeltaDto>
) {
    companion object {
        fun from(diff: ObservationDiff, from: String, to: String): ContextDiffResponse = ContextDiffResponse(
            fromEventId = diff.fromEventId.value.toString(),
            toEventId = diff.toEventId.value.toString(),
            from = from,
            to = to,
            addedBlocks = diff.addedBlocks.map { ContentBlockDto.from(it) },
            removedBlocks = diff.removedBlocks.map { ContentBlockDto.from(it) },
            modifiedBlocks = diff.modifiedBlocks.map { ContentBlockDeltaDto.from(it) }
        )
    }
}

data class ContentBlockDto(
    val blockType: String,
    val content: String,
    val tokenCount: Int,
    val source: String?
) {
    companion object {
        fun from(block: ContentBlock): ContentBlockDto = ContentBlockDto(
            blockType = block.blockType,
            content = block.content,
            tokenCount = block.tokenCount,
            source = block.source
        )
    }
}

data class ContentBlockDeltaDto(
    val blockType: String,
    val before: ContentBlockDto,
    val after: ContentBlockDto
) {
    companion object {
        fun from(delta: ContentBlockDelta): ContentBlockDeltaDto = ContentBlockDeltaDto(
            blockType = delta.blockType,
            before = ContentBlockDto.from(delta.before),
            after = ContentBlockDto.from(delta.after)
        )
    }
}

data class AuditEventResponse(
    val eventId: String,
    val sessionId: String,
    val agentId: String,
    val tenantId: String,
    val eventType: String,
    val occurredAt: String,
    val payload: JsonNode,
    val contextHash: String?,
    val parentEventId: String?,
    val schemaVersion: Int
) {
    companion object {
        fun from(event: MemoryEvent, objectMapper: ObjectMapper): AuditEventResponse = AuditEventResponse(
            eventId = event.id.value.toString(),
            sessionId = event.sessionId.value.toString(),
            agentId = event.agentId.value.toString(),
            tenantId = event.tenantId.value.toString(),
            eventType = event.eventType.name,
            occurredAt = event.occurredAt.toString(),
            payload = objectMapper.readTree(event.payload),
            contextHash = event.contextHash?.value,
            parentEventId = event.parentEventId?.value?.toString(),
            schemaVersion = event.schemaVersion.value
        )
    }
}
