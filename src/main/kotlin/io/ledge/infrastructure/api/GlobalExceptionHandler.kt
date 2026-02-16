package io.ledge.infrastructure.api

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        val message = ex.message ?: "Bad request"
        val isNotFound = message.contains("not found", ignoreCase = true)
        val status = if (isNotFound) HttpStatus.NOT_FOUND else HttpStatus.BAD_REQUEST
        return ResponseEntity.status(status).body(
            ErrorResponse(error = status.reasonPhrase, message = message, status = status.value())
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ErrorResponse> {
        val message = ex.message ?: "Conflict"
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(error = "Conflict", message = message, status = 409)
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(error = "Internal Server Error", message = "An unexpected error occurred", status = 500)
        )
    }
}
