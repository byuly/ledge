package io.ledge.tenant.application

data class RegisterAgentCommand(
    val name: String,
    val description: String,
    val metadata: Map<String, String> = emptyMap()
)
