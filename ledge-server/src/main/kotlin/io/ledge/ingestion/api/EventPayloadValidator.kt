package io.ledge.ingestion.api

import com.fasterxml.jackson.databind.JsonNode
import io.ledge.ingestion.domain.EventType

object EventPayloadValidator {

    fun validate(eventType: EventType, payload: JsonNode) {
        when (eventType) {
            EventType.USER_INPUT -> validateUserInput(payload)
            EventType.CONTEXT_ASSEMBLED -> validateContextAssembled(payload)
            EventType.INFERENCE_REQUESTED -> validateInferenceRequested(payload)
            EventType.INFERENCE_COMPLETED -> validateInferenceCompleted(payload)
            EventType.REASONING_TRACE -> validateReasoningTrace(payload)
            EventType.AGENT_OUTPUT -> validateAgentOutput(payload)
            EventType.TOOL_INVOKED -> validateToolInvoked(payload)
            EventType.TOOL_RESPONDED -> validateToolResponded(payload)
            EventType.ERROR -> validateError(payload)
            EventType.SESSION_COMPLETED, EventType.SESSION_ABANDONED -> { /* internal — no validation */ }
        }
    }

    private fun validateUserInput(payload: JsonNode) {
        requireObject("USER_INPUT", payload)
        requireString("USER_INPUT", payload, "content")
        requireString("USER_INPUT", payload, "inputType")
    }

    private fun validateContextAssembled(payload: JsonNode) {
        requireObject("CONTEXT_ASSEMBLED", payload)
        requireArray("CONTEXT_ASSEMBLED", payload, "blocks")
        requireNumber("CONTEXT_ASSEMBLED", payload, "totalTokens")
    }

    private fun validateInferenceRequested(payload: JsonNode) {
        requireObject("INFERENCE_REQUESTED", payload)
        requireString("INFERENCE_REQUESTED", payload, "modelId")
        requireString("INFERENCE_REQUESTED", payload, "provider")
    }

    private fun validateInferenceCompleted(payload: JsonNode) {
        requireObject("INFERENCE_COMPLETED", payload)
        requireString("INFERENCE_COMPLETED", payload, "response")
        requireString("INFERENCE_COMPLETED", payload, "finishReason")
        requireNumber("INFERENCE_COMPLETED", payload, "latencyMs")
        requireString("INFERENCE_COMPLETED", payload, "modelId")
        val usage = requireObjectField("INFERENCE_COMPLETED", payload, "usage")
        requireNumber("INFERENCE_COMPLETED", usage, "promptTokens", "usage.")
        requireNumber("INFERENCE_COMPLETED", usage, "completionTokens", "usage.")
        requireNumber("INFERENCE_COMPLETED", usage, "totalTokens", "usage.")
    }

    private fun validateReasoningTrace(payload: JsonNode) {
        requireObject("REASONING_TRACE", payload)
        requireString("REASONING_TRACE", payload, "thinkingContent")
        requireNumber("REASONING_TRACE", payload, "thinkingTokenCount")
    }

    private fun validateAgentOutput(payload: JsonNode) {
        requireObject("AGENT_OUTPUT", payload)
        requireString("AGENT_OUTPUT", payload, "content")
        requireString("AGENT_OUTPUT", payload, "outputType")
        requireString("AGENT_OUTPUT", payload, "inferenceEventId")
    }

    private fun validateToolInvoked(payload: JsonNode) {
        requireObject("TOOL_INVOKED", payload)
        requireString("TOOL_INVOKED", payload, "toolName")
        requireString("TOOL_INVOKED", payload, "toolId")
        requireObjectField("TOOL_INVOKED", payload, "parameters")
    }

    private fun validateToolResponded(payload: JsonNode) {
        requireObject("TOOL_RESPONDED", payload)
        requireString("TOOL_RESPONDED", payload, "toolName")
        requireString("TOOL_RESPONDED", payload, "toolInvokedEventId")
        requireString("TOOL_RESPONDED", payload, "result")
        requireNumber("TOOL_RESPONDED", payload, "durationMs")
        requireBoolean("TOOL_RESPONDED", payload, "success")
    }

    private fun validateError(payload: JsonNode) {
        requireObject("ERROR", payload)
        requireString("ERROR", payload, "errorType")
        requireString("ERROR", payload, "message")
        requireBoolean("ERROR", payload, "recoverable")
    }

    private fun requireObject(eventType: String, payload: JsonNode) {
        require(payload.isObject) {
            "Payload for $eventType must be a JSON object"
        }
    }

    private fun requireField(eventType: String, payload: JsonNode, field: String, prefix: String = ""): JsonNode {
        val node = payload.get(field)
        require(node != null && !node.isNull) {
            "Payload for $eventType is missing required field '$prefix$field'"
        }
        return node
    }

    private fun requireString(eventType: String, payload: JsonNode, field: String, prefix: String = "") {
        val node = requireField(eventType, payload, field, prefix)
        require(node.isTextual) {
            "Payload for $eventType requires '$prefix$field' to be a string"
        }
    }

    private fun requireNumber(eventType: String, payload: JsonNode, field: String, prefix: String = "") {
        val node = requireField(eventType, payload, field, prefix)
        require(node.isNumber) {
            "Payload for $eventType requires '$prefix$field' to be a number"
        }
    }

    private fun requireArray(eventType: String, payload: JsonNode, field: String, prefix: String = "") {
        val node = requireField(eventType, payload, field, prefix)
        require(node.isArray) {
            "Payload for $eventType requires '$prefix$field' to be an array"
        }
    }

    private fun requireObjectField(eventType: String, payload: JsonNode, field: String, prefix: String = ""): JsonNode {
        val node = requireField(eventType, payload, field, prefix)
        require(node.isObject) {
            "Payload for $eventType requires '$prefix$field' to be an object"
        }
        return node
    }

    private fun requireBoolean(eventType: String, payload: JsonNode, field: String, prefix: String = "") {
        val node = requireField(eventType, payload, field, prefix)
        require(node.isBoolean) {
            "Payload for $eventType requires '$prefix$field' to be a boolean"
        }
    }
}
