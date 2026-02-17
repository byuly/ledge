package io.ledge.sdk.spring

import io.ledge.sdk.LedgeClient
import io.ledge.sdk.LedgeSession
import io.ledge.sdk.event.ContentBlock
import io.ledge.sdk.event.TokenUsage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions

class LedgeObservationAdvisorTest {

    private lateinit var ledgeClient: LedgeClient
    private lateinit var session: LedgeSession
    private lateinit var advisor: LedgeObservationAdvisor
    private lateinit var chain: CallAroundAdvisorChain

    @BeforeEach
    fun setup() {
        ledgeClient = mock()
        session = mock()
        chain = mock()

        whenever(ledgeClient.createSession("test-agent")).thenReturn(session)
        whenever(session.sessionId).thenReturn("sess-1")
        whenever(session.agentId).thenReturn("test-agent")
        whenever(session.contextAssembled(any())).thenReturn("evt-ctx")
        whenever(session.inferenceRequested(any(), any())).thenReturn("evt-inf")
        whenever(session.inferenceCompleted(any(), any(), anyOrNull())).thenReturn("evt-comp")
        whenever(session.agentOutput(any(), anyOrNull())).thenReturn("evt-out")

        advisor = LedgeObservationAdvisor(ledgeClient, "test-agent")
    }

    @Test
    fun `emits correct event sequence for simple chat`() {
        val chatOptions = ChatOptions.builder().model("gpt-4o").build()
        val request = AdvisedRequest.builder()
            .chatModel(mock())
            .chatOptions(chatOptions)
            .systemText("You are helpful")
            .userText("Hello")
            .build()

        val assistantMessage = AssistantMessage("Hi there!")
        val metadata = ChatResponseMetadata.builder()
            .usage(DefaultUsage(10, 20))
            .build()
        val chatResponse = ChatResponse(listOf(Generation(assistantMessage)), metadata)
        val advisedResponse = AdvisedResponse(chatResponse, emptyMap())
        whenever(chain.nextAroundCall(any())).thenReturn(advisedResponse)

        advisor.aroundCall(request, chain)

        verify(ledgeClient).createSession("test-agent")
        verify(session).contextAssembled(argThat<List<ContentBlock>> {
            size == 2 && get(0).role == "system" && get(1).role == "user"
        })
        verify(session).inferenceRequested("gpt-4o", "spring-ai")
        verify(session).inferenceCompleted(eq("Hi there!"), any(), eq("evt-inf"))
        verify(session).agentOutput("Hi there!", "evt-inf")
        verify(ledgeClient).flush()
    }

    @Test
    fun `emits tool events when tool calls present`() {
        val chatOptions = ChatOptions.builder().model("gpt-4o").build()
        val request = AdvisedRequest.builder()
            .chatModel(mock())
            .chatOptions(chatOptions)
            .userText("Search for weather")
            .build()

        val toolCall = AssistantMessage.ToolCall("tc-1", "function", "get_weather", """{"city":"NYC"}""")
        val assistantMessage = AssistantMessage("", emptyMap(), listOf(toolCall))
        val chatResponse = ChatResponse(listOf(Generation(assistantMessage)))
        val advisedResponse = AdvisedResponse(chatResponse, emptyMap())
        whenever(chain.nextAroundCall(any())).thenReturn(advisedResponse)
        whenever(session.toolInvoked(any(), any(), anyOrNull())).thenReturn("evt-tool")

        advisor.aroundCall(request, chain)

        verify(session).toolInvoked(
            eq("get_weather"),
            eq(mapOf("arguments" to """{"city":"NYC"}""")),
            eq("evt-inf")
        )
    }
}
