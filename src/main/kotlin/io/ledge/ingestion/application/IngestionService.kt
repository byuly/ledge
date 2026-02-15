package io.ledge.ingestion.application

import io.ledge.ingestion.application.port.DomainEventPublisher
import io.ledge.ingestion.application.port.MemoryEventPublisher
import io.ledge.ingestion.application.port.MemoryEventQuery
import io.ledge.ingestion.application.port.SessionRepository
import io.ledge.ingestion.domain.MemoryEvent
import io.ledge.ingestion.domain.Session
import io.ledge.shared.DomainEvent
import io.ledge.shared.EventId
import io.ledge.shared.SessionId
import io.ledge.shared.TenantId

class IngestionService(
    private val sessionRepository: SessionRepository,
    private val memoryEventPublisher: MemoryEventPublisher,
    private val domainEventPublisher: DomainEventPublisher,
    private val memoryEventQuery: MemoryEventQuery
) {

    fun createSession(tenantId: TenantId, command: CreateSessionCommand): Session {
        val session = Session(
            id = SessionId.generate(),
            agentId = command.agentId,
            tenantId = tenantId
        )
        return sessionRepository.save(session)
    }

    fun getSession(sessionId: SessionId, tenantId: TenantId): Session? {
        return sessionRepository.findById(sessionId, tenantId)
    }

    fun completeSession(sessionId: SessionId, tenantId: TenantId): Session {
        val session = sessionRepository.findById(sessionId, tenantId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        session.complete()
        sessionRepository.save(session)
        domainEventPublisher.publish(
            DomainEvent.SessionCompleted(
                sessionId = session.id,
                agentId = session.agentId,
                tenantId = session.tenantId
            )
        )
        return session
    }

    fun abandonSession(sessionId: SessionId, tenantId: TenantId): Session {
        val session = sessionRepository.findById(sessionId, tenantId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        session.abandon()
        sessionRepository.save(session)
        return session
    }

    fun ingestEvent(tenantId: TenantId, command: IngestEventCommand): IngestEventResult {
        val session = sessionRepository.findById(command.sessionId, tenantId)
            ?: throw IllegalArgumentException("Session not found: ${command.sessionId}")
        val eventId = EventId.generate()
        val event = session.ingest(
            eventId = eventId,
            eventType = command.eventType,
            payload = command.payload,
            occurredAt = command.occurredAt,
            contextHash = command.contextHash,
            parentEventId = command.parentEventId,
            schemaVersion = command.schemaVersion
        )
        sessionRepository.save(session)
        memoryEventPublisher.publish(event)
        return IngestEventResult(eventId = event.id, sequenceNumber = event.sequenceNumber)
    }

    fun ingestBatch(tenantId: TenantId, commands: List<IngestEventCommand>): List<IngestEventResult> {
        require(commands.isNotEmpty()) { "Batch must not be empty" }
        require(commands.size <= 500) { "Batch size must not exceed 500, got ${commands.size}" }

        val sessionIds = commands.map { it.sessionId }.distinct()
        val sessions = sessionIds.associateWith { id ->
            sessionRepository.findById(id, tenantId)
                ?: throw IllegalArgumentException("Session not found: $id")
        }

        val results = mutableListOf<IngestEventResult>()
        val allEvents = mutableListOf<MemoryEvent>()

        for (command in commands) {
            val session = sessions[command.sessionId]!!
            val eventId = EventId.generate()
            val event = session.ingest(
                eventId = eventId,
                eventType = command.eventType,
                payload = command.payload,
                occurredAt = command.occurredAt,
                contextHash = command.contextHash,
                parentEventId = command.parentEventId,
                schemaVersion = command.schemaVersion
            )
            results.add(IngestEventResult(eventId = event.id, sequenceNumber = event.sequenceNumber))
            allEvents.add(event)
        }

        sessions.values.forEach { sessionRepository.save(it) }
        memoryEventPublisher.publishAll(allEvents)

        return results
    }

    fun getSessionEvents(
        sessionId: SessionId,
        tenantId: TenantId,
        afterSequenceNumber: Long? = null,
        limit: Int = 50
    ): List<MemoryEvent> {
        sessionRepository.findById(sessionId, tenantId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        return memoryEventQuery.findBySessionId(sessionId, tenantId, afterSequenceNumber, limit)
    }

    fun purgeTenantSessions(tenantId: TenantId) {
        sessionRepository.deleteByTenantId(tenantId)
    }
}
