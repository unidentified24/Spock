package io.github.unidentified24.spock.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Background quality search publishes usable candidates early; its caller owns the deadline. */
suspend fun <T:Any> bestPrepared(
    lookups:List<StreamLookup<T>>,rank:(T)->Int,timeoutMs:Long=45000,
    onImprovement:suspend (T)->Unit,
):T? = supervisorScope {
    val results=Channel<T?>(Channel.UNLIMITED)
    var best:T?=null
    val jobs=lookups.map { lookup -> launch {
        val value=try{lookup.resolve()}catch(c:CancellationException){currentCoroutineContext().ensureActive();null}catch(_:Exception){null}
        results.trySend(value)
    }}
    try {
        withTimeoutOrNull(timeoutMs){repeat(lookups.size){
            val candidate=results.receive()
            if(candidate!=null && (best==null || rank(candidate)>rank(best!!))){
                onImprovement(candidate);best=candidate
            }
        }}
        best
    } finally {
        jobs.forEach{it.cancel()};lookups.forEach{runCatching{it.cancel()}};results.close()
    }
}
