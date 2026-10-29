package io.github.unidentified24.spock.data.preferences

class PlaybackClock {
    private var last: Long? = null
    private var active = false
    private var track = ""
    fun sample(now: Long, playing: Boolean, key: String): Long {
        val elapsed = last?.let { now - it } ?: 0L
        val counted = if (active && playing && track == key && elapsed in 1..1000) elapsed else 0L
        last = now; active = playing; track = key
        return counted
    }
}
