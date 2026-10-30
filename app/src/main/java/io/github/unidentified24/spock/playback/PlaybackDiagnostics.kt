package io.github.unidentified24.spock.playback

/** Local, bounded diagnostics: deliberately never store URLs, tokens or account data. */
object PlaybackDiagnostics {
    private val events=java.util.concurrent.ConcurrentLinkedDeque<String>()
    fun event(stage:String) {
        events.addLast("${java.time.LocalTime.now().withNano((java.time.LocalTime.now().nano/1000000)*1000000)} $stage")
        while(events.size>80)events.pollFirst()
    }
    fun report():String = "Spock ${io.github.unidentified24.spock.BuildConfig.VERSION_NAME}\n"+events.joinToString("\n")
}
