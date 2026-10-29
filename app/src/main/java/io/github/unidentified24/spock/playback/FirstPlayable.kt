package io.github.unidentified24.spock.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/** A miss/exception is not a winner. Only the first usable value leaves this scope. */
data class StreamLookup<T>(val cancel: () -> Unit = {}, val resolve: suspend () -> T?)

suspend fun <T : Any> firstPlayable(lookups: List<StreamLookup<T>>, timeoutMs: Long = 45_000): T? = supervisorScope {
    if (lookups.isEmpty()) return@supervisorScope null
    val results = Channel<T?>(Channel.UNLIMITED)
    val jobs = lookups.map { lookup ->
        launch {
            val value = try {
                lookup.resolve()
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                null // A source's own timeout/cancellation must not kill its competitors.
            } catch (_: Exception) {
                null
            }
            results.trySend(value)
        }
    }
    try {
        withTimeoutOrNull(timeoutMs) {
            repeat(lookups.size) {
                results.receive()?.let { return@withTimeoutOrNull it }
            }
            null
        }
    } finally {
        // Abort blocking HTTP before joining cancelled children; no wait for slow sockets.
        jobs.forEach { it.cancel() }
        lookups.forEach { runCatching { it.cancel() } }
        results.close()
    }
}
