package io.ledge.infrastructure.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ledge.shared.TenantId
import io.ledge.tenant.application.port.TenantRepository
import io.ledge.tenant.domain.Tenant
import io.ledge.tenant.domain.TenantStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.security.MessageDigest
import java.time.Duration
import java.util.Optional

@Component
class ApiKeyAuthFilter(
    private val tenantRepository: TenantRepository,
    private val redisTemplate: ReactiveStringRedisTemplate,
    @Value("\${ledge.rate-limit.requests-per-minute:1000}") private val rateLimit: Long,
    private val objectMapper: ObjectMapper
) : WebFilter, Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        val method = exchange.request.method

        if (isExempt(path, method)) {
            return chain.filter(exchange)
        }

        val rawKey = exchange.request.headers.getFirst("X-API-Key")
            ?: return errorResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing X-API-Key header")

        val keyHash = sha256(rawKey)

        return Mono.fromCallable { Optional.ofNullable(tenantRepository.findByApiKeyHash(keyHash)) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { opt ->
                val tenant = opt.orElse(null)
                    ?: return@flatMap errorResponse(exchange, HttpStatus.UNAUTHORIZED, "Invalid API key")
                when (tenant.status) {
                    TenantStatus.SUSPENDED -> errorResponse(exchange, HttpStatus.FORBIDDEN, "Tenant is suspended")
                    TenantStatus.DELETED -> errorResponse(exchange, HttpStatus.FORBIDDEN, "Tenant is deleted")
                    TenantStatus.ACTIVE -> checkRateLimit(tenant.id)
                        .flatMap { allowed ->
                            if (!allowed) {
                                errorResponse(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded")
                            } else {
                                val mutated = exchange.mutate()
                                    .request { it.header("X-Tenant-Id", tenant.id.toString()) }
                                    .build()
                                chain.filter(mutated)
                            }
                        }
                }
            }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isExempt(path: String, method: HttpMethod): Boolean {
        return (method == HttpMethod.POST && path == "/api/v1/tenants")
            || (method == HttpMethod.DELETE && path.matches(Regex("/api/v1/tenants/[^/]+")))
            || path.startsWith("/actuator")
    }

    private fun errorResponse(exchange: ServerWebExchange, status: HttpStatus, message: String): Mono<Void> {
        val response = exchange.response
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON
        val body = objectMapper.writeValueAsBytes(
            ErrorResponse(error = status.reasonPhrase, message = message, status = status.value())
        )
        val buffer = response.bufferFactory().wrap(body)
        return response.writeWith(Mono.just(buffer))
    }

    private fun checkRateLimit(tenantId: TenantId): Mono<Boolean> {
        val key = "tenant:${tenantId.value}:ratelimit"
        return redisTemplate.opsForValue().increment(key)
            .flatMap { count ->
                if (count == 1L) {
                    redisTemplate.expire(key, Duration.ofSeconds(60)).thenReturn(true)
                } else {
                    Mono.just(count <= rateLimit)
                }
            }
    }
}
