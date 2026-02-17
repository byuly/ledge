package io.ledge.sdk.spring

import io.ledge.sdk.LedgeClient
import io.ledge.sdk.event.ContentBlock
import io.ledge.sdk.event.TokenUsage
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatResponse

class LedgeObservationAdvisor(
    private val ledgeClient: LedgeClient,
    private val agentId: String
) : CallAroundAdvisor {

    override fun getName(): String = "LedgeObservationAdvisor"

    override fun getOrder(): Int = 0

    override fun aroundCall(request: AdvisedRequest, chain: CallAroundAdvisorChain): AdvisedResponse {
        val session = ledgeClient.createSession(agentId)

        val blocks = buildContextBlocks(request)
        if (blocks.isNotEmpty()) {
            session.contextAssembled(blocks)
        }

        val modelName = request.chatOptions()?.getModel() ?: "unknown"
        val inferenceEventId = session.inferenceRequested(modelName, "spring-ai")

        val response = chain.nextAroundCall(request)

        val chatResponse = response.response()

        if (chatResponse != null) {
            val assistantMessage = chatResponse.result?.output
            val content = assistantMessage?.getText() ?: ""
            val usage = extractTokenUsage(chatResponse)
            session.inferenceCompleted(content, usage, inferenceEventId)

            val toolCalls = assistantMessage?.toolCalls
            if (!toolCalls.isNullOrEmpty()) {
                for (toolCall in toolCalls) {
                    session.toolInvoked(
                        toolCall.name(),
                        mapOf("arguments" to toolCall.arguments()),
                        inferenceEventId
                    )
                }
            }

            if (content.isNotBlank()) {
                session.agentOutput(content, inferenceEventId)
            }
        }

        ledgeClient.flush()
        return response
    }

    private fun buildContextBlocks(request: AdvisedRequest): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()

        val systemText = request.systemText()
        if (!systemText.isNullOrBlank()) {
            blocks.add(ContentBlock("system", systemText))
        }

        val userText = request.userText()
        if (!userText.isNullOrBlank()) {
            blocks.add(ContentBlock("user", userText))
        }

        return blocks
    }

    private fun extractTokenUsage(chatResponse: ChatResponse): TokenUsage {
        val metadata = chatResponse.metadata as? ChatResponseMetadata
        val usage = metadata?.usage
        return if (usage != null) {
            TokenUsage(
                promptTokens = usage.promptTokens ?: 0,
                completionTokens = usage.completionTokens ?: 0,
                totalTokens = usage.totalTokens ?: 0
            )
        } else {
            TokenUsage(0, 0, 0)
        }
    }
}
