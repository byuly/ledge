package io.ledge.memory.domain

@JvmInline
value class Confidence(val value: Float) {
    init {
        require(value in 0.0f..1.0f) {
            "Confidence must be between 0.0 and 1.0, got $value"
        }
    }
}
