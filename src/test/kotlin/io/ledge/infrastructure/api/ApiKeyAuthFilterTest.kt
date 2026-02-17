package io.ledge.infrastructure.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.TestFixtures
import io.ledge.tenant.application.FakeTenantRepository
import io.ledge.tenant.domain.TenantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.MessageDigest

class ApiKeyAuthFilterTest {

    private val tenantRepo = FakeTenantRepository()
    private val mockOpsForValue = mock<ReactiveValueOperations<String, String>>()
    private val mockRedisTemplate = mock<ReactiveStringRedisTemplate>()
    private val filter = ApiKeyAuthFilter(
        tenantRepository = tenantRepo,
        redisTemplate = mockRedisTemplate,
        rateLimit = 1000L,
        objectMapper = ObjectMapper()
    )

    private val passThroughChain = WebFilterChain { Mono.empty() }

    @BeforeEach
    fun setUpRedisMock() {
        whenever(mockRedisTemplate.opsForValue()).thenReturn(mockOpsForValue)
        whenever(mockOpsForValue.increment(any())).thenReturn(Mono.just(1L))
        whenever(mockRedisTemplate.expire(any(), any())).thenReturn(Mono.just(true))
    }

    @Test
    fun `missing X-API-Key header returns 401`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/events").build()
        )

        filter.filter(exchange, passThroughChain).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `unknown API key returns 401`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/events")
                .header("X-API-Key", "unknown-key")
                .build()
        )

        filter.filter(exchange, passThroughChain).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `suspended tenant returns 403`() {
        val rawKey = "suspended-tenant-key"
        val tenant = TestFixtures.tenant(
            apiKeyHash = sha256(rawKey),
            status = TenantStatus.SUSPENDED
        )
        tenantRepo.save(tenant)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/events")
                .header("X-API-Key", rawKey)
                .build()
        )

        filter.filter(exchange, passThroughChain).block()

        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `deleted tenant returns 403`() {
        val rawKey = "deleted-tenant-key"
        val tenant = TestFixtures.tenant(
            apiKeyHash = sha256(rawKey),
            status = TenantStatus.DELETED
        )
        tenantRepo.save(tenant)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/events")
                .header("X-API-Key", rawKey)
                .build()
        )

        filter.filter(exchange, passThroughChain).block()

        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `valid key injects X-Tenant-Id and passes through`() {
        val rawKey = "valid-active-key"
        val tenant = TestFixtures.tenant(apiKeyHash = sha256(rawKey))
        tenantRepo.save(tenant)

        var capturedExchange: ServerWebExchange? = null
        val capturingChain = WebFilterChain { ex ->
            capturedExchange = ex
            Mono.empty()
        }
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/events")
                .header("X-API-Key", rawKey)
                .build()
        )

        filter.filter(exchange, capturingChain).block()

        assertNotNull(capturedExchange)
        assertEquals(
            tenant.id.toString(),
            capturedExchange!!.request.headers.getFirst("X-Tenant-Id")
        )
    }

    @Test
    fun `exempt path POST tenants bypasses auth`() {
        // No X-API-Key set, but path is exempt
        var chainCalled = false
        val trackingChain = WebFilterChain { ex ->
            chainCalled = true
            Mono.empty()
        }
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/tenants").build()
        )

        filter.filter(exchange, trackingChain).block()

        assert(chainCalled) { "Chain should be called for exempt path" }
    }

    @Test
    fun `exempt path DELETE tenant bypasses auth`() {
        var chainCalled = false
        val trackingChain = WebFilterChain { ex ->
            chainCalled = true
            Mono.empty()
        }
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.delete("/api/v1/tenants/some-tenant-id").build()
        )

        filter.filter(exchange, trackingChain).block()

        assert(chainCalled) { "Chain should be called for exempt DELETE /tenants/{id}" }
    }

    @Test
    fun `actuator path bypasses auth`() {
        var chainCalled = false
        val trackingChain = WebFilterChain { ex ->
            chainCalled = true
            Mono.empty()
        }
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/actuator/health").build()
        )

        filter.filter(exchange, trackingChain).block()

        assert(chainCalled) { "Chain should be called for actuator path" }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
