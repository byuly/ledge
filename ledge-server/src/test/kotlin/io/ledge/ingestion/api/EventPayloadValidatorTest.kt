package io.ledge.ingestion.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.ledge.ingestion.domain.EventType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class EventPayloadValidatorTest {

    private val mapper = ObjectMapper()
    private fun json(raw: String) = mapper.readTree(raw)

    // --- Cross-cutting ---

    @Test
    fun `non-object payload throws for user-submitted event types`() {
        val nonObjects = listOf("[]", "\"hello\"", "42", "true", "null")
        val userTypes = EventType.entries.filter {
            it != EventType.SESSION_COMPLETED && it != EventType.SESSION_ABANDONED
        }
        for (type in userTypes) {
            for (raw in nonObjects) {
                assertThrows<IllegalArgumentException>("$type with $raw") {
                    EventPayloadValidator.validate(type, json(raw))
                }
            }
        }
    }

    @Test
    fun `extra fields are allowed`() {
        assertDoesNotThrow {
            EventPayloadValidator.validate(
                EventType.USER_INPUT,
                json("""{"content":"hi","inputType":"text","extra":"ok"}""")
            )
        }
    }

    @Test
    fun `SESSION_COMPLETED accepts any payload`() {
        assertDoesNotThrow { EventPayloadValidator.validate(EventType.SESSION_COMPLETED, json("{}")) }
        assertDoesNotThrow { EventPayloadValidator.validate(EventType.SESSION_COMPLETED, json("[]")) }
        assertDoesNotThrow { EventPayloadValidator.validate(EventType.SESSION_COMPLETED, json("null")) }
    }

    @Test
    fun `SESSION_ABANDONED accepts any payload`() {
        assertDoesNotThrow { EventPayloadValidator.validate(EventType.SESSION_ABANDONED, json("{}")) }
        assertDoesNotThrow { EventPayloadValidator.validate(EventType.SESSION_ABANDONED, json("42")) }
    }

    // --- USER_INPUT ---

    @Test
    fun `USER_INPUT valid payload passes`() {
        assertDoesNotThrow {
            EventPayloadValidator.validate(EventType.USER_INPUT, json("""{"content":"hi","inputType":"text"}"""))
        }
    }

    @Test
    fun `USER_INPUT missing content throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.USER_INPUT, json("""{"inputType":"text"}"""))
        }
    }

    @Test
    fun `USER_INPUT missing inputType throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.USER_INPUT, json("""{"content":"hi"}"""))
        }
    }

    @Test
    fun `USER_INPUT content wrong type throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.USER_INPUT, json("""{"content":123,"inputType":"text"}"""))
        }
    }

    // --- CONTEXT_ASSEMBLED ---

    @Test
    fun `CONTEXT_ASSEMBLED valid payload passes`() {
        assertDoesNotThrow {
            EventPayloadValidator.validate(EventType.CONTEXT_ASSEMBLED, json("""{"blocks":[],"totalTokens":100}"""))
        }
    }

    @Test
    fun `CONTEXT_ASSEMBLED missing blocks throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.CONTEXT_ASSEMBLED, json("""{"totalTokens":100}"""))
        }
    }

    @Test
    fun `CONTEXT_ASSEMBLED missing totalTokens throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.CONTEXT_ASSEMBLED, json("""{"blocks":[]}"""))
        }
    }

    @Test
    fun `CONTEXT_ASSEMBLED blocks wrong type throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.CONTEXT_ASSEMBLED, json("""{"blocks":"not-array","totalTokens":1}"""))
        }
    }

    @Test
    fun `CONTEXT_ASSEMBLED totalTokens wrong type throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.CONTEXT_ASSEMBLED, json("""{"blocks":[],"totalTokens":"100"}"""))
        }
    }

    // --- INFERENCE_REQUESTED ---

    @Test
    fun `INFERENCE_REQUESTED valid payload passes`() {
        assertDoesNotThrow {
            EventPayloadValidator.validate(EventType.INFERENCE_REQUESTED, json("""{"modelId":"gpt-4","provider":"openai"}"""))
        }
    }

    @Test
    fun `INFERENCE_REQUESTED missing modelId throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_REQUESTED, json("""{"provider":"openai"}"""))
        }
    }

    @Test
    fun `INFERENCE_REQUESTED missing provider throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_REQUESTED, json("""{"modelId":"gpt-4"}"""))
        }
    }

    @Test
    fun `INFERENCE_REQUESTED modelId wrong type throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_REQUESTED, json("""{"modelId":4,"provider":"openai"}"""))
        }
    }

    // --- INFERENCE_COMPLETED ---

    @Test
    fun `INFERENCE_COMPLETED valid payload passes`() {
        assertDoesNotThrow {
            EventPayloadValidator.validate(EventType.INFERENCE_COMPLETED, json("""
                {"response":"hi","finishReason":"stop","latencyMs":120,"modelId":"gpt-4",
                 "usage":{"promptTokens":10,"completionTokens":5,"totalTokens":15}}
            """.trimIndent()))
        }
    }

    @Test
    fun `INFERENCE_COMPLETED missing response throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_COMPLETED, json("""
                {"finishReason":"stop","latencyMs":120,"modelId":"gpt-4",
                 "usage":{"promptTokens":10,"completionTokens":5,"totalTokens":15}}
            """.trimIndent()))
        }
    }

    @Test
    fun `INFERENCE_COMPLETED missing usage throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_COMPLETED, json("""
                {"response":"hi","finishReason":"stop","latencyMs":120,"modelId":"gpt-4"}
            """.trimIndent()))
        }
    }

    @Test
    fun `INFERENCE_COMPLETED usage missing promptTokens throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_COMPLETED, json("""
                {"response":"hi","finishReason":"stop","latencyMs":120,"modelId":"gpt-4",
                 "usage":{"completionTokens":5,"totalTokens":15}}
            """.trimIndent()))
        }
    }

    @Test
    fun `INFERENCE_COMPLETED usage missing completionTokens throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_COMPLETED, json("""
                {"response":"hi","finishReason":"stop","latencyMs":120,"modelId":"gpt-4",
                 "usage":{"promptTokens":10,"totalTokens":15}}
            """.trimIndent()))
        }
    }

    @Test
    fun `INFERENCE_COMPLETED usage missing totalTokens throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_COMPLETED, json("""
                {"response":"hi","finishReason":"stop","latencyMs":120,"modelId":"gpt-4",
                 "usage":{"promptTokens":10,"completionTokens":5}}
            """.trimIndent()))
        }
    }

    @Test
    fun `INFERENCE_COMPLETED usage not object throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_COMPLETED, json("""
                {"response":"hi","finishReason":"stop","latencyMs":120,"modelId":"gpt-4","usage":"bad"}
            """.trimIndent()))
        }
    }

    @Test
    fun `INFERENCE_COMPLETED missing finishReason throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_COMPLETED, json("""
                {"response":"hi","latencyMs":120,"modelId":"gpt-4",
                 "usage":{"promptTokens":10,"completionTokens":5,"totalTokens":15}}
            """.trimIndent()))
        }
    }

    @Test
    fun `INFERENCE_COMPLETED missing latencyMs throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_COMPLETED, json("""
                {"response":"hi","finishReason":"stop","modelId":"gpt-4",
                 "usage":{"promptTokens":10,"completionTokens":5,"totalTokens":15}}
            """.trimIndent()))
        }
    }

    @Test
    fun `INFERENCE_COMPLETED missing modelId throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.INFERENCE_COMPLETED, json("""
                {"response":"hi","finishReason":"stop","latencyMs":120,
                 "usage":{"promptTokens":10,"completionTokens":5,"totalTokens":15}}
            """.trimIndent()))
        }
    }

    // --- REASONING_TRACE ---

    @Test
    fun `REASONING_TRACE valid payload passes`() {
        assertDoesNotThrow {
            EventPayloadValidator.validate(EventType.REASONING_TRACE, json("""{"thinkingContent":"hmm","thinkingTokenCount":5}"""))
        }
    }

    @Test
    fun `REASONING_TRACE missing thinkingContent throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.REASONING_TRACE, json("""{"thinkingTokenCount":5}"""))
        }
    }

    @Test
    fun `REASONING_TRACE missing thinkingTokenCount throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.REASONING_TRACE, json("""{"thinkingContent":"hmm"}"""))
        }
    }

    @Test
    fun `REASONING_TRACE thinkingTokenCount wrong type throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.REASONING_TRACE, json("""{"thinkingContent":"hmm","thinkingTokenCount":"five"}"""))
        }
    }

    // --- AGENT_OUTPUT ---

    @Test
    fun `AGENT_OUTPUT valid payload passes`() {
        assertDoesNotThrow {
            EventPayloadValidator.validate(EventType.AGENT_OUTPUT, json("""{"content":"hi","outputType":"text","inferenceEventId":"e1"}"""))
        }
    }

    @Test
    fun `AGENT_OUTPUT missing content throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.AGENT_OUTPUT, json("""{"outputType":"text","inferenceEventId":"e1"}"""))
        }
    }

    @Test
    fun `AGENT_OUTPUT missing outputType throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.AGENT_OUTPUT, json("""{"content":"hi","inferenceEventId":"e1"}"""))
        }
    }

    @Test
    fun `AGENT_OUTPUT missing inferenceEventId throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.AGENT_OUTPUT, json("""{"content":"hi","outputType":"text"}"""))
        }
    }

    // --- TOOL_INVOKED ---

    @Test
    fun `TOOL_INVOKED valid payload passes`() {
        assertDoesNotThrow {
            EventPayloadValidator.validate(EventType.TOOL_INVOKED, json("""{"toolName":"search","toolId":"t1","parameters":{}}"""))
        }
    }

    @Test
    fun `TOOL_INVOKED missing toolName throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.TOOL_INVOKED, json("""{"toolId":"t1","parameters":{}}"""))
        }
    }

    @Test
    fun `TOOL_INVOKED missing toolId throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.TOOL_INVOKED, json("""{"toolName":"search","parameters":{}}"""))
        }
    }

    @Test
    fun `TOOL_INVOKED missing parameters throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.TOOL_INVOKED, json("""{"toolName":"search","toolId":"t1"}"""))
        }
    }

    @Test
    fun `TOOL_INVOKED parameters wrong type throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.TOOL_INVOKED, json("""{"toolName":"search","toolId":"t1","parameters":"bad"}"""))
        }
    }

    // --- TOOL_RESPONDED ---

    @Test
    fun `TOOL_RESPONDED valid payload passes`() {
        assertDoesNotThrow {
            EventPayloadValidator.validate(EventType.TOOL_RESPONDED, json("""
                {"toolName":"search","toolInvokedEventId":"e1","result":"ok","durationMs":50,"success":true}
            """.trimIndent()))
        }
    }

    @Test
    fun `TOOL_RESPONDED missing toolName throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.TOOL_RESPONDED, json("""
                {"toolInvokedEventId":"e1","result":"ok","durationMs":50,"success":true}
            """.trimIndent()))
        }
    }

    @Test
    fun `TOOL_RESPONDED missing toolInvokedEventId throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.TOOL_RESPONDED, json("""
                {"toolName":"search","result":"ok","durationMs":50,"success":true}
            """.trimIndent()))
        }
    }

    @Test
    fun `TOOL_RESPONDED missing result throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.TOOL_RESPONDED, json("""
                {"toolName":"search","toolInvokedEventId":"e1","durationMs":50,"success":true}
            """.trimIndent()))
        }
    }

    @Test
    fun `TOOL_RESPONDED missing durationMs throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.TOOL_RESPONDED, json("""
                {"toolName":"search","toolInvokedEventId":"e1","result":"ok","success":true}
            """.trimIndent()))
        }
    }

    @Test
    fun `TOOL_RESPONDED missing success throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.TOOL_RESPONDED, json("""
                {"toolName":"search","toolInvokedEventId":"e1","result":"ok","durationMs":50}
            """.trimIndent()))
        }
    }

    @Test
    fun `TOOL_RESPONDED success wrong type throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.TOOL_RESPONDED, json("""
                {"toolName":"search","toolInvokedEventId":"e1","result":"ok","durationMs":50,"success":"yes"}
            """.trimIndent()))
        }
    }

    // --- ERROR ---

    @Test
    fun `ERROR valid payload passes`() {
        assertDoesNotThrow {
            EventPayloadValidator.validate(EventType.ERROR, json("""{"errorType":"TIMEOUT","message":"timed out","recoverable":true}"""))
        }
    }

    @Test
    fun `ERROR missing errorType throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.ERROR, json("""{"message":"timed out","recoverable":true}"""))
        }
    }

    @Test
    fun `ERROR missing message throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.ERROR, json("""{"errorType":"TIMEOUT","recoverable":true}"""))
        }
    }

    @Test
    fun `ERROR missing recoverable throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.ERROR, json("""{"errorType":"TIMEOUT","message":"timed out"}"""))
        }
    }

    @Test
    fun `ERROR recoverable wrong type throws`() {
        assertThrows<IllegalArgumentException> {
            EventPayloadValidator.validate(EventType.ERROR, json("""{"errorType":"TIMEOUT","message":"timed out","recoverable":"yes"}"""))
        }
    }
}
