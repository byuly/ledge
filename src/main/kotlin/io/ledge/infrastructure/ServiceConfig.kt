package io.ledge.infrastructure

import io.ledge.ingestion.application.IngestionService
import io.ledge.ingestion.application.port.MemoryEventPublisher
import io.ledge.ingestion.application.port.MemoryEventQuery
import io.ledge.ingestion.application.port.SessionRepository
import io.ledge.tenant.application.TenantService
import io.ledge.tenant.application.port.AgentRepository
import io.ledge.tenant.application.port.TenantRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ServiceConfig {

    @Bean
    fun ingestionService(
        sessionRepository: SessionRepository,
        memoryEventPublisher: MemoryEventPublisher,
        domainEventPublisher: io.ledge.ingestion.application.port.DomainEventPublisher,
        memoryEventQuery: MemoryEventQuery
    ): IngestionService = IngestionService(sessionRepository, memoryEventPublisher, domainEventPublisher, memoryEventQuery)

    @Bean
    fun tenantService(
        tenantRepository: TenantRepository,
        agentRepository: AgentRepository,
        domainEventPublisher: io.ledge.tenant.application.port.DomainEventPublisher
    ): TenantService = TenantService(tenantRepository, agentRepository, domainEventPublisher)
}
