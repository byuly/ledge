package io.ledge.sdk.transport

import io.ledge.sdk.model.IngestBatchRequest
import io.ledge.sdk.model.IngestBatchResponse
import io.ledge.sdk.model.IngestEventRequest
import io.ledge.sdk.model.IngestEventResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class EventBatcherTest {

    private fun testEvent(index: Int = 0) = IngestEventRequest(
        sessionId = "session-1",
        agentId = "agent-1",
        eventType = "USER_INPUT",
        occurredAt = "2024-01-01T00:00:00Z",
        payload = mapOf("content" to "test-$index")
    )

    @Test
    fun `flushes when batch size is reached`() {
        val batches = CopyOnWriteArrayList<List<IngestEventRequest>>()
        val transport = capturingTransport(batches)
        val batcher = EventBatcher(transport, batchSize = 3, flushIntervalMs = 10000)

        repeat(3) { batcher.enqueue(testEvent(it)) }
        Thread.sleep(50) // Give time for flush

        assertEquals(1, batches.size)
        assertEquals(3, batches[0].size)
        batcher.close()
    }

    @Test
    fun `flushes on time interval`() {
        val batches = CopyOnWriteArrayList<List<IngestEventRequest>>()
        val transport = capturingTransport(batches)
        val batcher = EventBatcher(transport, batchSize = 100, flushIntervalMs = 50)

        batcher.enqueue(testEvent(0))
        Thread.sleep(200)

        assertTrue(batches.size >= 1)
        batcher.close()
    }

    @Test
    fun `drains remaining events on close`() {
        val batches = CopyOnWriteArrayList<List<IngestEventRequest>>()
        val transport = capturingTransport(batches)
        val batcher = EventBatcher(transport, batchSize = 100, flushIntervalMs = 10000)

        repeat(5) { batcher.enqueue(testEvent(it)) }
        batcher.close()

        val totalEvents = batches.sumOf { it.size }
        assertEquals(5, totalEvents)
    }

    @Test
    fun `rejects enqueue after close`() {
        val transport = capturingTransport(CopyOnWriteArrayList())
        val batcher = EventBatcher(transport, batchSize = 50, flushIntervalMs = 100)
        batcher.close()

        assertThrows<IllegalStateException> {
            batcher.enqueue(testEvent())
        }
    }

    @Test
    fun `immediate mode - flush sends events right away`() {
        val batches = CopyOnWriteArrayList<List<IngestEventRequest>>()
        val transport = capturingTransport(batches)
        val batcher = EventBatcher(transport, batchSize = 100, flushIntervalMs = 10000)

        batcher.enqueue(testEvent(0))
        batcher.enqueue(testEvent(1))
        batcher.flush()

        assertEquals(1, batches.size)
        assertEquals(2, batches[0].size)
        batcher.close()
    }

    private fun capturingTransport(batches: CopyOnWriteArrayList<List<IngestEventRequest>>): HttpTransport {
        return object : HttpTransport {
            @Suppress("UNCHECKED_CAST")
            override fun <T> post(path: String, body: Any, responseType: Class<T>): T {
                if (body is IngestBatchRequest) {
                    batches.add(body.events.toList())
                }
                return IngestBatchResponse(
                    accepted = (body as? IngestBatchRequest)?.events?.size ?: 0,
                    results = (body as? IngestBatchRequest)?.events?.mapIndexed { i, _ ->
                        IngestEventResponse("evt-$i")
                    } ?: emptyList()
                ) as T
            }

            override fun <T> get(path: String, responseType: Class<T>): T = throw UnsupportedOperationException()
            override fun <T> patch(path: String, body: Any, responseType: Class<T>): T = throw UnsupportedOperationException()
            override fun patchNoContent(path: String, body: Any) {}
            override fun <T> postAsync(path: String, body: Any, responseType: Class<T>): CompletableFuture<T> = CompletableFuture.supplyAsync { post(path, body, responseType) }
            override fun <T> getAsync(path: String, responseType: Class<T>): CompletableFuture<T> = throw UnsupportedOperationException()
            override fun <T> patchAsync(path: String, body: Any, responseType: Class<T>): CompletableFuture<T> = throw UnsupportedOperationException()
            override fun patchNoContentAsync(path: String, body: Any): CompletableFuture<Void?> = CompletableFuture.completedFuture(null)
        }
    }
}
