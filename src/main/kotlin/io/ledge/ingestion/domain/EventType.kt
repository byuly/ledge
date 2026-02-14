package io.ledge.ingestion.domain

enum class EventType {
    USER_MESSAGE,
    ASSISTANT_MESSAGE,
    TOOL_CALL,
    TOOL_RESULT,
    ERROR,
    CONTEXT_SWITCH,
    FEEDBACK,
    CORRECTION,
    PREFERENCE_EXPRESSED,
    FACT_STATED,
    TASK_COMPLETED
}
