package io.ledge.infrastructure.api

data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)
