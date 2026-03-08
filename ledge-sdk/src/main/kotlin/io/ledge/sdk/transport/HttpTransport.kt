package io.ledge.sdk.transport

import java.util.concurrent.CompletableFuture

interface HttpTransport {
    fun <T> post(path: String, body: Any, responseType: Class<T>): T
    fun <T> get(path: String, responseType: Class<T>): T
    fun <T> patch(path: String, body: Any, responseType: Class<T>): T
    fun patchNoContent(path: String, body: Any)

    fun <T> postAsync(path: String, body: Any, responseType: Class<T>): CompletableFuture<T>
    fun <T> getAsync(path: String, responseType: Class<T>): CompletableFuture<T>
    fun <T> patchAsync(path: String, body: Any, responseType: Class<T>): CompletableFuture<T>
    fun patchNoContentAsync(path: String, body: Any): CompletableFuture<Void?>
}
