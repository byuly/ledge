package io.ledge.infrastructure

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.clickhouse.ClickHouseContainer
import java.sql.DriverManager

object TestContainers {

    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("ledge")
        .withUsername("test")
        .withPassword("test")

    val clickhouse: ClickHouseContainer = ClickHouseContainer("clickhouse/clickhouse-server:24.3")
        .withUsername("default")
        .withPassword("test")
        .withEnv("CLICKHOUSE_PASSWORD", "test")

    val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
        .withExposedPorts(6379)

    init {
        postgres.start()
        clickhouse.start()
        redis.start()
        initPostgresSchema()
        initClickHouseSchema()
    }

    private fun initPostgresSchema() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE tenants (
                        tenant_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        name        TEXT NOT NULL,
                        api_key_hash TEXT NOT NULL UNIQUE,
                        status      TEXT NOT NULL DEFAULT 'ACTIVE',
                        created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    );
                    CREATE TABLE agents (
                        agent_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        tenant_id   UUID NOT NULL REFERENCES tenants(tenant_id),
                        name        TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        metadata    JSONB DEFAULT '{}',
                        created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    );
                    CREATE TABLE sessions (
                        session_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        agent_id            UUID NOT NULL REFERENCES agents(agent_id),
                        tenant_id           UUID NOT NULL REFERENCES tenants(tenant_id),
                        started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        ended_at            TIMESTAMPTZ,
                        status              TEXT NOT NULL DEFAULT 'ACTIVE',
                        metadata            JSONB DEFAULT '{}'
                    );
                    CREATE INDEX idx_sessions_agent_tenant ON sessions(agent_id, tenant_id);
                    """.trimIndent()
                )
            }
        }
    }

    private fun initClickHouseSchema() {
        val ch = clickhouse as JdbcDatabaseContainer<*>
        DriverManager.getConnection(ch.jdbcUrl, ch.username, ch.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE DATABASE IF NOT EXISTS ledge")
            }
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS ledge.memory_events (
                        event_id        UUID,
                        session_id      UUID,
                        agent_id        UUID,
                        tenant_id       UUID,
                        event_type      LowCardinality(String),
                        occurred_at     DateTime64(3, 'UTC'),
                        payload         String,
                        context_hash    String,
                        parent_event_id Nullable(UUID),
                        schema_version  Int32
                    )
                    ENGINE = MergeTree()
                    PARTITION BY toYYYYMM(occurred_at)
                    ORDER BY (tenant_id, agent_id, occurred_at)
                    """.trimIndent()
                )
            }
        }
    }

    fun buildDatabaseClient(): DatabaseClient {
        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "postgresql")
            .option(ConnectionFactoryOptions.HOST, postgres.host)
            .option(ConnectionFactoryOptions.PORT, postgres.getMappedPort(5432))
            .option(ConnectionFactoryOptions.DATABASE, "ledge")
            .option(ConnectionFactoryOptions.USER, "test")
            .option(ConnectionFactoryOptions.PASSWORD, "test")
            .build()
        val connectionFactory: ConnectionFactory = ConnectionFactories.get(options)
        return DatabaseClient.create(connectionFactory)
    }

    fun clickHouseUrl(): String {
        val ch = clickhouse as JdbcDatabaseContainer<*>
        return "${ch.jdbcUrl}?user=${ch.username}&password=${ch.password}"
    }

    fun redisHost(): String = redis.host

    fun redisPort(): Int = redis.getMappedPort(6379)
}
