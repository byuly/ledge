package io.ledge.infrastructure

import io.ledge.ingestion.application.IngestionService
import io.ledge.ingestion.application.port.MemoryEventPublisher
import io.ledge.ingestion.application.port.MemoryEventQuery
import io.ledge.ingestion.application.port.SessionRepository
import io.ledge.memory.application.MemoryService
import io.ledge.memory.application.port.MemoryEntryRepository
import io.ledge.memory.application.port.MemorySnapshotRepository
import io.ledge.memory.application.port.ObservationEventQuery
import io.ledge.tenant.application.TenantService
import io.ledge.tenant.application.port.AgentRepository
import io.ledge.tenant.application.port.TenantRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ServiceConfig {

    @Bean
    fun ingestionService(
        sessionRepository: SessionRepository,
        memoryEventPublisher: MemoryEventPublisher,
        domainEventPublisher: io.ledge.ingestion.application.port.DomainEventPublisher,
        memoryEventQuery: MemoryEventQuery,
        meterRegistry: MeterRegistry
    ): IngestionService = IngestionService(sessionRepository, memoryEventPublisher, domainEventPublisher, memoryEventQuery, meterRegistry)

    @Bean
    fun tenantService(
        tenantRepository: TenantRepository,
        agentRepository: AgentRepository,
        domainEventPublisher: io.ledge.tenant.application.port.DomainEventPublisher
    ): TenantService = TenantService(tenantRepository, agentRepository, domainEventPublisher)

    @Bean
    fun memoryService(
        snapshotRepository: MemorySnapshotRepository,
        entryRepository: MemoryEntryRepository,
        domainEventPublisher: io.ledge.memory.application.port.DomainEventPublisher,
        observationEventQuery: ObservationEventQuery,
        meterRegistry: MeterRegistry
    ): MemoryService = MemoryService(snapshotRepository, entryRepository, domainEventPublisher, observationEventQuery, meterRegistry)

    @Bean
    fun activeSessionsGauge(
        meterRegistry: MeterRegistry,
        sessionRepository: SessionRepository
    ): Any {
        meterRegistry.gauge("ledge.sessions.active", sessionRepository) { it.countActive().toDouble() }
        return Object()
    }
}
