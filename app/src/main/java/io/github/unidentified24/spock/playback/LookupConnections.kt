package io.github.unidentified24.spock.playback

import java.net.HttpURLConnection
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ThreadContextElement

/** Request-local ownership; cancelling one lookup never touches another song's HTTP. */
class LookupConnections : ThreadContextElement<LookupConnections?>,
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<LookupConnections> {
        private val active = ThreadLocal<LookupConnections?>()
        init {
            com.metrolist.innertube.ExtractionCalls.register = { call -> active.get()?.own(call) }
        }
        fun track(connection: HttpURLConnection) { active.get()?.register(connection) }
        fun current(): LookupConnections? = active.get()
        fun trackCall(call: okhttp3.Call): okhttp3.Call = call.also { active.get()?.own(it) }
        fun checkActive() {
            active.get()?.let { owner ->
                synchronized(owner) { if (owner.closed) throw CancellationException("Lookup superseded") }
            }
        }
    }
    private val connections = mutableListOf<HttpURLConnection>()
    private val calls = mutableListOf<okhttp3.Call>()
    private var closed = false
    fun own(call: okhttp3.Call) {
        synchronized(this) {
            if (!closed) { calls.add(call); return }
        }
        call.cancel()
        throw CancellationException("Lookup superseded")
    }
    private fun register(connection: HttpURLConnection) {
        synchronized(this) {
            if (!closed) { connections.add(connection); return }
        }
        connection.disconnect()
        throw CancellationException("Lookup superseded")
    }
    fun close() {
        val (pending, activeCalls) = synchronized(this) {
            closed = true
            (connections.toList() to calls.toList()).also { connections.clear(); calls.clear() }
        }
        activeCalls.forEach { it.cancel() }
        pending.forEach { runCatching { it.disconnect() } }
    }
    override fun updateThreadContext(context: CoroutineContext): LookupConnections? = active.get().also { active.set(this) }
    override fun restoreThreadContext(context: CoroutineContext, oldState: LookupConnections?) { active.set(oldState) }
}
