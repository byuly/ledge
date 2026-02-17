package io.ledge.sdk

import io.ledge.sdk.event.ContentBlock
import io.ledge.sdk.event.EventType
import io.ledge.sdk.event.ObservationEvent
import io.ledge.sdk.event.TokenUsage
import io.ledge.sdk.hash.ContextHasher
import java.time.Instant

class LedgeSession internal constructor(
    val sessionId: String,
    val agentId: String,
    private val client: LedgeClient
) {
    @Volatile
    var lastEventId: String? = null
        internal set

    fun userInput(content: String): String {
        return emit(
            EventType.USER_INPUT,
            mapOf("content" to content)
        )
    }

    fun contextAssembled(blocks: List<ContentBlock>): String {
        val blockMaps = blocks.map { mapOf("role" to it.role, "content" to it.content, "tokenCount" to it.tokenCount) }
        val contextHash = ContextHasher.hashContentBlocks(blockMaps)
        return emit(
            EventType.CONTEXT_ASSEMBLED,
            mapOf("blocks" to blockMaps),
            contextHash = contextHash
        )
    }

    fun inferenceRequested(model: String, provider: String): String {
        return emit(
            EventType.INFERENCE_REQUESTED,
            mapOf("model" to model, "provider" to provider)
        )
    }

    fun inferenceCompleted(
        content: String,
        tokenUsage: TokenUsage,
        parentEventId: String? = null
    ): String {
        return emit(
            EventType.INFERENCE_COMPLETED,
            mapOf(
                "content" to content,
                "tokenUsage" to mapOf(
                    "promptTokens" to tokenUsage.promptTokens,
                    "completionTokens" to tokenUsage.completionTokens,
                    "totalTokens" to tokenUsage.totalTokens
                )
            ),
            parentEventId = parentEventId
        )
    }

    fun reasoningTrace(trace: String, parentEventId: String? = null): String {
        return emit(
            EventType.REASONING_TRACE,
            mapOf("trace" to trace),
            parentEventId = parentEventId
        )
    }

    fun agentOutput(content: String, parentEventId: String? = null): String {
        return emit(
            EventType.AGENT_OUTPUT,
            mapOf("content" to content),
            parentEventId = parentEventId
        )
    }

    fun toolInvoked(toolName: String, arguments: Map<String, Any?>, parentEventId: String? = null): String {
        return emit(
            EventType.TOOL_INVOKED,
            mapOf("toolName" to toolName, "arguments" to arguments),
            parentEventId = parentEventId
        )
    }

    fun toolResponded(toolName: String, result: String, parentEventId: String? = null): String {
        return emit(
            EventType.TOOL_RESPONDED,
            mapOf("toolName" to toolName, "result" to result),
            parentEventId = parentEventId
        )
    }

    fun error(errorMessage: String, errorType: String? = null, parentEventId: String? = null): String {
        val payload = mutableMapOf<String, Any?>("message" to errorMessage)
        if (errorType != null) payload["type"] = errorType
        return emit(
            EventType.ERROR,
            payload,
            parentEventId = parentEventId
        )
    }

    fun emit(
        eventType: EventType,
        payload: Map<String, Any?>,
        contextHash: String? = null,
        parentEventId: String? = null
    ): String {
        val effectiveParent = parentEventId ?: lastEventId
        val event = ObservationEvent(
            sessionId = sessionId,
            agentId = agentId,
            eventType = eventType,
            occurredAt = Instant.now(),
            payload = payload,
            contextHash = contextHash,
            parentEventId = effectiveParent
        )
        val eventId = client.emit(event)
        lastEventId = eventId
        return eventId
    }
}
