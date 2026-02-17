package io.ledge.sdk.transport

open class LedgeApiException(
    val statusCode: Int,
    override val message: String
) : RuntimeException("HTTP $statusCode: $message")

class RetryableException(
    statusCode: Int,
    message: String,
    val retryAfterMs: Long? = null
) : LedgeApiException(statusCode, message)
