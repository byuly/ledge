package io.ledge.memory.domain

@JvmInline
value class ContentHash(val value: String) {
    init {
        require(value.matches(SHA256_PATTERN)) {
            "ContentHash must be a valid SHA-256 hex string"
        }
    }

    private companion object {
        val SHA256_PATTERN = Regex("^[a-f0-9]{64}$")
    }
}
