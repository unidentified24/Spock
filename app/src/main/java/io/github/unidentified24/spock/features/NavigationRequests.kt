package io.github.unidentified24.spock.features

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NavigationRequests {
    data class Request(val page:String,val serial:Long)
    private val pending=MutableStateFlow<Request?>(null)
    val state=pending.asStateFlow()
    private val allowed=setOf("downloads","library","home","search","local","history","stats","player","account","queue","mixes")
    @Synchronized fun open(page:String?){if(page in allowed)pending.value=Request(page!!,(pending.value?.serial ?: System.nanoTime())+1)}
    fun consumed(request:Request){pending.compareAndSet(request,null)}
}
