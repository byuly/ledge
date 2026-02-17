package io.ledge.sdk.transport

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ledge.sdk.LedgeConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

class JdkHttpTransport(
    private val config: LedgeConfig,
    private val objectMapper: ObjectMapper = defaultObjectMapper(),
    private val retryPolicy: RetryPolicy = RetryPolicy(maxRetries = config.maxRetries)
) : HttpTransport {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.connectTimeoutMs))
        .build()

    override fun <T> post(path: String, body: Any, responseType: Class<T>): T =
        retryPolicy.execute { doRequest("POST", path, body, responseType) }

    override fun <T> get(path: String, responseType: Class<T>): T =
        retryPolicy.execute { doRequest("GET", path, null, responseType) }

    override fun <T> patch(path: String, body: Any, responseType: Class<T>): T =
        retryPolicy.execute { doRequest("PATCH", path, body, responseType) }

    override fun <T> postAsync(path: String, body: Any, responseType: Class<T>): CompletableFuture<T> =
        CompletableFuture.supplyAsync { post(path, body, responseType) }

    override fun <T> getAsync(path: String, responseType: Class<T>): CompletableFuture<T> =
        CompletableFuture.supplyAsync { get(path, responseType) }

    override fun <T> patchAsync(path: String, body: Any, responseType: Class<T>): CompletableFuture<T> =
        CompletableFuture.supplyAsync { patch(path, body, responseType) }

    private fun <T> doRequest(method: String, path: String, body: Any?, responseType: Class<T>): T {
        val url = "${config.baseUrl.trimEnd('/')}$path"
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("X-API-Key", config.apiKey)
            .timeout(Duration.ofMillis(config.requestTimeoutMs))

        val bodyPublisher = if (body != null) {
            HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))
        } else {
            HttpRequest.BodyPublishers.noBody()
        }

        builder.method(method, bodyPublisher)

        val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return handleResponse(response, responseType)
    }

    private fun <T> handleResponse(response: HttpResponse<String>, responseType: Class<T>): T {
        val statusCode = response.statusCode()
        return when {
            statusCode in 200..299 -> objectMapper.readValue(response.body(), responseType)
            statusCode == 429 || statusCode in 500..599 -> {
                val retryAfter = response.headers().firstValue("Retry-After")
                    .map { it.toLongOrNull()?.times(1000) }
                    .orElse(null)
                throw RetryableException(statusCode, response.body(), retryAfter)
            }
            else -> throw LedgeApiException(statusCode, response.body())
        }
    }

    companion object {
        fun defaultObjectMapper(): ObjectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
