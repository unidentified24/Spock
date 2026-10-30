package io.github.unidentified24.spock.playback

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LookupConnectionsTest {
    private class Call : okhttp3.Call {
        var cancelled = false
        override fun request() = okhttp3.Request.Builder().url("https://example.invalid").build()
        override fun execute(): okhttp3.Response = error("No network in ownership tests")
        override fun enqueue(responseCallback: okhttp3.Callback) = error("No network in ownership tests")
        override fun cancel() { cancelled = true }
        override fun isExecuted() = false
        override fun isCanceled() = cancelled
        override fun timeout() = okio.Timeout.NONE
        override fun clone(): okhttp3.Call = Call()
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    }
    @Test fun cancelsOwnedOkHttpCallsWithoutCancellingOtherLookups() = runBlocking {
        val a = Call(); val b = Call(); val late = Call()
        val first = LookupConnections(); val second = LookupConnections()
        withContext(first) { LookupConnections.trackCall(a) }
        withContext(second) { LookupConnections.trackCall(b) }
        first.close()
        assertTrue(a.isCanceled())
        assertFalse(b.isCanceled())
        try {
            withContext(first) { LookupConnections.trackCall(late) }
            fail("late network calls must be rejected")
        } catch (_: CancellationException) { }
        assertTrue(late.isCanceled())
        second.close()
        assertTrue(b.isCanceled())
    }

    @Test fun extractorFallbackUsesTheSameIsolatedOwnership() = runBlocking {
        val owner=LookupConnections();val owned=Call();val unrelated=Call()
        withContext(owner) {com.metrolist.innertube.ExtractionCalls.track(owned)}
        com.metrolist.innertube.ExtractionCalls.track(unrelated)
        owner.close()
        assertTrue(owned.isCanceled())
        assertFalse(unrelated.isCanceled())
    }

    @Test fun winnerAbortsABlockingLookupBeforeReturning() = runBlocking {
        withTimeout(3000) {
            val opened = CompletableDeferred<Unit>()
            val disconnected = java.util.concurrent.CountDownLatch(1)
            val connections = LookupConnections()
            val socket = object : HttpURLConnection(URL("https://example.invalid")) {
                override fun connect() = Unit
                override fun usingProxy() = false
                override fun disconnect() { disconnected.countDown() }
            }
            val result = firstPlayable(listOf(
                StreamLookup(cancel = { connections.close() }) {
                    withContext(Dispatchers.IO + connections) {
                        LookupConnections.track(socket)
                        opened.complete(Unit)
                        check(disconnected.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                            "losing HTTP connection was not aborted"
                        }
                        "late FLAC"
                    }
                },
                StreamLookup { opened.await(); "YouTube" },
            ))
            assertEquals("YouTube", result)
            assertEquals(0L, disconnected.count)
        }
    }

    private class Connection : HttpURLConnection(URL("https://example.invalid")) {
        var closed = false
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun connect() = Unit
    }
    @Test fun cancellationClosesOnlyThisLookupAndRejectsNewConnections() = runBlocking {
        val first = LookupConnections(); val second = LookupConnections()
        val a = Connection(); val b = Connection(); val late = Connection()
        withContext(first) { LookupConnections.track(a) }
        withContext(second) { LookupConnections.track(b) }
        first.close()
        assertTrue(a.closed)
        assertFalse(b.closed)
        try {
            withContext(first) { LookupConnections.track(late) }
            fail("closed lookups must not start another request")
        } catch (_: CancellationException) { }
        assertTrue(late.closed)
        second.close()
        assertTrue(b.closed)
    }
}
