package io.ledge.sdk.transport

import io.ledge.sdk.event.ObservationEvent
import io.ledge.sdk.model.IngestBatchRequest
import io.ledge.sdk.model.IngestBatchResponse
import io.ledge.sdk.model.IngestEventRequest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EventBatcher(
    private val transport: HttpTransport,
    private val batchSize: Int = 50,
    private val flushIntervalMs: Long = 100
) {

    private val queue = ConcurrentLinkedQueue<IngestEventRequest>()
    private val closed = AtomicBoolean(false)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ledge-batcher").apply { isDaemon = true }
    }

    init {
        scheduler.scheduleAtFixedRate(::flushIfNeeded, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS)
    }

    fun enqueue(event: IngestEventRequest) {
        check(!closed.get()) { "EventBatcher is closed" }
        queue.add(event)
        if (queue.size >= batchSize) {
            flush()
        }
    }

    fun flush() {
        val batch = drain(batchSize)
        if (batch.isNotEmpty()) {
            sendBatch(batch)
        }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            scheduler.shutdown()
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
            // Drain remaining events
            while (queue.isNotEmpty()) {
                val batch = drain(batchSize)
                if (batch.isNotEmpty()) {
                    sendBatch(batch)
                }
            }
        }
    }

    private fun flushIfNeeded() {
        if (queue.isNotEmpty()) {
            flush()
        }
    }

    private fun drain(max: Int): List<IngestEventRequest> {
        val batch = mutableListOf<IngestEventRequest>()
        var count = 0
        while (count < max) {
            val event = queue.poll() ?: break
            batch.add(event)
            count++
        }
        return batch
    }

    private fun sendBatch(batch: List<IngestEventRequest>) {
        try {
            transport.post(
                "/api/v1/events/batch",
                IngestBatchRequest(events = batch),
                IngestBatchResponse::class.java
            )
        } catch (e: Exception) {
            // In production SDK, we'd want a configurable error handler
            // For now, log and drop to avoid blocking the caller
            System.err.println("ledge-sdk: Failed to send batch of ${batch.size} events: ${e.message}")
        }
    }
}
