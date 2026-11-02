package io.github.unidentified24.spock.playback

/** Pure guard for starting radio discovery early enough to prepare a gapless next track. */
object ContinuousPlaybackPolicy {
    fun shouldPrefetchRadio(
        playbackWanted:Boolean,
        failed:Boolean,
        offlineOnly:Boolean,
        hasNext:Boolean,
        remainingMs:Long,
    ):Boolean = playbackWanted && !failed && !offlineOnly && !hasNext && remainingMs in 1L..60_000L
}
