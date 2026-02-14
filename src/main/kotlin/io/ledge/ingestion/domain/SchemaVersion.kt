package io.ledge.ingestion.domain

@JvmInline
value class SchemaVersion(val value: Int) {
    init {
        require(value > 0) { "SchemaVersion must be positive, got $value" }
    }
}
