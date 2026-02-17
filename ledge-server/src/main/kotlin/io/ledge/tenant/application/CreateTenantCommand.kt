package io.ledge.tenant.application

data class CreateTenantCommand(
    val name: String,
    val apiKeyHash: String
)
