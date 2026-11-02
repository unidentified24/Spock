package io.github.unidentified24.spock.playback

/** Pure gate for recovering silent native buffering without undoing intentional pauses. */
object StallRecoveryPolicy {
    const val DEFAULT_TIMEOUT_MS=12_000L
    fun shouldRecover(
        playbackWanted:Boolean,
        nativeEngine:Boolean,
        crossfading:Boolean,
        playWhenReady:Boolean,
        ended:Boolean,
        buffering:Boolean,
        playing:Boolean,
        elapsedWithoutProgressMs:Long,
        timeoutMs:Long=DEFAULT_TIMEOUT_MS,
    ):Boolean = playbackWanted && nativeEngine && !crossfading && playWhenReady && !ended &&
        (buffering || playing) && elapsedWithoutProgressMs>=timeoutMs
}
